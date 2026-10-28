# Password recovery

Password recovery is available only for active users who have completed their initial password setup. The public API never reveals whether an email belongs to an account.

## Flow

1. `POST /api/auth/forgot-password` accepts `{"email":"user@example.com"}` and returns `202` with an empty body for both known and unknown accounts.
2. For an eligible account, the backend invalidates earlier unused reset links and sends a new one through the configured corporate SMTP server.
3. `POST /api/auth/reset-password` accepts `{"token":"...","password":"..."}`. The same password policy applies to recovery and initial password setup: at least 12 characters, A-Z, a-z, a digit and a special character from `!@#$%^&*()_+-=`. The API accepts up to 128 characters; BCrypt limits the encoded password to 72 UTF-8 bytes.
4. A valid token is consumed in the same database transaction as the BCrypt password update. The user's current refresh-session generation is revoked in Redis, which invalidates every existing access and refresh session.

Reset tokens contain 256 random bits, expire after 30 minutes by default, and are stored only as SHA-256 hashes. A token cannot be reused. Expired, consumed, invalidated, malformed and unknown tokens receive the same `410 Gone` response.

Disabled users and accounts still awaiting their initial invitation receive the same forgot-password response as an unknown email, without an email being sent. A successful reset also clears the temporary failed-login counter.

The shared policy rejects common password bases and predictable variants, `workflow`, the account email, and name or email-local-part components of at least two characters. Matching ignores case, separators, accents and common substitutions such as `@` for `a` or `0` for `o`. Validation uses the current account data before hashing or changing the password. A policy violation returns `400` with a password field violation; it neither consumes the link nor revokes the user's existing sessions, so the same link can be retried with a valid password.

## Mail failure

When password recovery mail is not configured, the request returns a generic `503` without account details. A delivery failure invalidates the newly created token before returning the same neutral error. Attempts and outcomes are logged with a one-way email fingerprint or user ID; email addresses, tokens and passwords are never written to logs.

## Configuration

| Variable | Purpose | Default |
| --- | --- | --- |
| `PASSWORD_RESET_TTL` | One-time link lifetime | `30m` |
| `PASSWORD_RESET_URL` | Frontend reset page; the token is appended as a query parameter | Required for delivery |
| `PASSWORD_RESET_MAIL_FROM` | Sender address for password recovery messages | Empty |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Corporate SMTP connection | Environment-specific |

The reset URL must use HTTPS outside local development. SMTP credentials belong in the deployment secret store and must not be committed.
