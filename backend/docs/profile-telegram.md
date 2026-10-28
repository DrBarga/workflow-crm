# Telegram binding from the profile (T6321)

Source: WorkflowCRM project wiki, Product Requirements Documents, Telegram binding; T6321 acceptance criteria.

## User flow

Open `/profile` in an authenticated browser. Request a link, open the bot or scan the QR code, press Start, and confirm the account email in the bot. Opening the link alone does not change the account. The page checks the profile every three seconds while visible and on focus, without reloading the document.

The QR code is rendered locally with `qrcode.react`; the link is not sent to a QR service. The client holds the link only in component memory and removes it after confirmation, expiry, sign-out or account switching. It does not save the token to browser storage.

## API

- `GET /api/profile`: current account id, email, full name, role, region and `telegram: { linked, revision }`. No password hash, Telegram chat ID or binding token is included. `revision` is an opaque audit event identifier, allowing replacement to be detected when `linked` remains true.
- `POST /api/profile/telegram/link`: current account only; response contains `link`, `expiresAt` and `expiresInSeconds: 600`. A new link invalidates the previous pending link for this account.
- `DELETE /api/profile/telegram`: remove the current binding, invalidate pending links and revoke the account sessions. The interface asks for confirmation and then explains that another sign-in is required.

All profile routes require the existing JWT cookie. Writes require the CSRF cookie and the header returned by `GET /api/auth/csrf`. Responses are not cached. A disabled session cannot request a binding link. Missing bot configuration or an invalid bot username returns 503 before issuing a token.

The existing `/api/auth/telegram/link` remains an initial-binding endpoint with its existing 15-minute lifetime. It cannot replace a bound chat. Profile links carry a server-side replacement permission and have a 10-minute lifetime. Old Redis payloads without this permission remain initial-binding tokens.

## Confirmation and storage

The existing webhook `POST /api/auth/telegram/webhook` requires `X-Telegram-Bot-Api-Secret-Token`. Binding is accepted only from a private chat whose chat id matches the sender id. Start shows the CRM email and confirmation/rejection buttons. Successful confirmation includes the email; expired, consumed and missing links receive clear instructions.

Redis stores SHA-256 token hashes with an expiry and an account index. Lua atomically consumes the token and invalidates older links. PostgreSQL locks the CRM user and enforces the unique Telegram chat constraint. Replacing a chat keeps the old binding until confirmation; a conflict or transaction failure preserves it. Disabling the account makes outstanding links unusable.

Binding writes `BOUND` or `REBOUND` to `telegram_binding_events`. Replacing a different chat also writes `UNBOUND` for the previous chat. User changes and audit records commit in the same transaction. Confirming the same chat advances the audit revision once; replaying the token adds no records.

No schema migration is required: the existing unique chat constraint and audit table are reused.

## Configuration and verification

Use the existing `TELEGRAM_BOT_USERNAME`, `TELEGRAM_BOT_TOKEN` and `TELEGRAM_WEBHOOK_SECRET` settings. Configure the shared project bot webhook according to the existing auth-device-confirmation guide. This change does not register a webhook or change credentials.

Backend tests cover the real profile and webhook controllers with PostgreSQL and Redis; the Telegram gateway is replaced by a test double. Browser checks cover the client with controlled API responses. A real Telegram chat on the deployed environment is a separate operational check after deployment.

Protocol references: [Telegram deep linking](https://core.telegram.org/bots/features#deep-linking), [qrcode.react](https://github.com/zpao/qrcode.react).
