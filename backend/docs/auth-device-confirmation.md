# Device confirmation and Telegram binding

The login endpoint validates credentials before it creates an authentication session. Password setup from the 72-hour invitation trusts the browser that sets the password. Its first login receives the profile response and access/refresh cookies without another challenge, even if Telegram has not been linked. An unknown device receives `202 Accepted` with a challenge ID and a server-issued `workflow_device` cookie; no JWT or refresh token is issued at that point. The browser must keep this cookie when it polls and completes the challenge.

The device cookie contains a random 256-bit value. Redis and refresh-session metadata contain only its SHA-256 digest. `X-Device-Id` is not an authentication factor. Device trust expires after `DEVICE_TRUST_DURATION` (30 days by default). `DEVICE_CONFIRMATION_MODE` accepts `NEW_DEVICE`, `PERIODICALLY`, or `EVERY_LOGIN`; `EVERY_LOGIN` asks for confirmation on subsequent logins, but the first login after password setup uses a one-time exception. The other modes respect the configured trust lifetime.

## Login flow

1. `POST /api/auth/login` with `email` and `password`. The confirmation channel is selected by the server for every role.
2. For an unknown device, the response contains `challengeId`, `status`, and `expiresInSeconds`. If Telegram is not bound, the server creates only an email challenge and sends a code immediately; it does not create a Telegram challenge. If Telegram is bound, the server sends a Telegram challenge to that chat with IP, browser, and time. Unavailable or failed Telegram delivery falls back to email.
3. `GET /api/auth/challenges/{id}` reports the state. Browser polling is the fallback if a live notification channel is unavailable.
4. Email challenges are confirmed with `POST /api/auth/challenges/{id}/email` and a six-digit `code` from the email. Successful confirmation approves the challenge even when Telegram has not been bound.
5. `POST /api/auth/challenges/{id}/complete` issues the access and refresh cookies after Telegram approval or successful email confirmation. The challenge and device cookie must belong to the same browser.

The initial Telegram and email challenges share the configurable `AUTH_CHALLENGE_TTL`, which defaults to five minutes. Email codes are stored only as challenge-bound SHA-256 digests and are consumed atomically with the challenge state transition. A code cannot be reused; invalid attempts are limited by `AUTH_EMAIL_CODE_MAX_ATTEMPTS`, which defaults to five. More than three login challenges per user within the Redis one-minute window are rejected. A Telegram resend is allowed no more frequently than once in ten seconds. A rejection blocks that device and invalidates its access and refresh sessions. Expired challenges do not grant access.

Binding links are valid only in a private chat with the bot. Invitation links expire after 72 hours; a link tied to a login challenge is valid only while that challenge remains active. Issuing a new link replaces the previous one, and an accepted or rejected link cannot be reused. An already bound account cannot silently switch to another Telegram chat.

Opening `/start <token>` shows the account email and asks for explicit confirmation. `/start` without a token asks the user to sign in and request a binding link; an expired or used token requires a new link. A Telegram chat already assigned to another account is rejected. Approval stores the chat ID; a post-login binding link does not change device trust. Rejection leaves Telegram unbound and does not block the already trusted device. Binding, rebinding, and unbinding are recorded in `telegram_binding_events`.

## Binding management

After the first login, authenticated users can inspect binding status at `GET /api/auth/telegram`, request a one-time deep link at `POST /api/auth/telegram/link`, and unbind at `DELETE /api/auth/telegram`. A superadmin can unbind a user through `DELETE /api/users/{id}/telegram`; binding links are available only to the signed-in user. The admin route reloads the caller's current role from the database. Unbinding invalidates all of that user's current sessions.

`POST /api/auth/telegram/webhook` handles the auth updates of the existing shared bot. It requires Telegram's `X-Telegram-Bot-Api-Secret-Token` header. The project's single webhook/router must continue dispatching notification and approval updates; do not register a second bot or replace the existing webhook with an auth-only handler without routing those other updates. Configure `TELEGRAM_BOT_USERNAME`, `TELEGRAM_BOT_TOKEN`, and `TELEGRAM_WEBHOOK_SECRET` from the existing bot, and configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and `AUTH_EMAIL_FROM` for the email fallback. The stored chat ID is used for later login challenges and is available for notification delivery. Login fails closed when the required delivery path is not configured.

For an isolated end-to-end check, point a separate test bot at a local or temporary backend with its own `TELEGRAM_BOT_USERNAME`, `TELEGRAM_BOT_TOKEN`, and `TELEGRAM_WEBHOOK_SECRET`. The webhook must reach `/api/auth/telegram/webhook` over public HTTPS. Create a test user, set the password, verify the first login on that browser, request `/api/auth/telegram/link` with the authenticated session, confirm the link in the test bot, and then sign in from another browser to exercise the Telegram challenge. Disable delivery through the test bot to check the email fallback. Do not replace the shared bot's webhook for this check.

State-changing browser requests remain CSRF-protected. The Telegram webhook uses the shared secret instead of a browser CSRF token. Neither JWTs nor email codes are returned in login responses or written to application logs.

## Email fallback configuration

| Variable | Purpose | Default |
| --- | --- | --- |
| `AUTH_CHALLENGE_TTL` | Lifetime shared by Telegram and email challenges | `5m` |
| `AUTH_EMAIL_CODE_MAX_ATTEMPTS` | Maximum code checks within one challenge | `5` |
| `AUTH_EMAIL_FROM` | Sender address for login confirmation messages | Empty |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Corporate SMTP connection | Environment-specific |

When Telegram is bound and available, it is used first for every role, including superadmin. If Telegram is not bound, is unavailable, or delivery fails, the user receives an email challenge. An unbound user may complete login with the one-time code and bind Telegram after signing in. If the required email delivery path is unavailable, login fails closed with a neutral service-unavailable response.
