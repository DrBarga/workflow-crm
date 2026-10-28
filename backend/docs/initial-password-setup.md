# Initial password setup

Creating a user sends one invitation email. The link opens `/set-password?token=...`; the invitation expires after 72 hours by default. The account remains disabled until password setup succeeds. Telegram is linked separately after sign-in.

## API

`GET /api/auth/invite/{token}` checks that the invitation exists, is unused and is within its TTL. A successful response is `200` with `{"deviceStatus":"PENDING_PASSWORD_SETUP"}` and an HttpOnly device cookie. The endpoint grants no trust and consumes no token. Responses are not cached. Invalid, expired, replaced or consumed tokens return `410` without a device cookie. The token is redacted from API error paths.

`POST /api/auth/set-password` accepts `{"token":"...","password":"..."}` and requires the CSRF cookie and header from `GET /api/auth/csrf`. It returns `204` with the device cookie after setting the password, activating the account, consuming the invitation and trusting the submitting browser. If no valid device cookie was presented, a new one is issued. The first login with email and password from this browser requires no Telegram or email confirmation. Other browsers follow the usual device confirmation flow.

The legacy `POST /api/user-invitations/open` and `/accept` endpoints remain supported and use the same implementation. The client uses the auth endpoints.

## Password rules

- 12 to 128 characters, with a maximum of 72 UTF-8 bytes for BCrypt.
- At least one uppercase Latin letter, one lowercase Latin letter, one digit and one character from `!@#$%^&*()_+-=`.
- No common password bases or predictable variants.
- No account email, name components or `workflow`; matching ignores case, separators, accents and common substitutions. Single-character initials are excluded from personal component matching.

Recovery uses the same shared policy. Rejections return `400`, `code=VALIDATION_FAILED` and a `violations` entry with `field=password` and the violated rule in `message`. Validation leaves the account, invitation, audit and device trust unchanged. The page displays the specific reason and allows another attempt using the same token.

## Single use and audit

The invitation row is locked during setup. Concurrent attempts using the same token cannot trust two browsers: one succeeds and subsequent attempts receive `410`. Merely opening the link from another browser grants no trust. Opening an already consumed link displays an invalid-link message asking the user to contact the administrator.

Password setup and token consumption share a database transaction with the user access history and two entries in `password_setup_events`: `INITIAL_PASSWORD_SET` and `INVITATION_DEVICE_TRUSTED`. Each event contains the user ID, a SHA-256 device identifier and a UTC timestamp. Passwords, raw device cookies and invitation tokens are excluded. Redis invitation trust is removed if the database transaction rolls back, so a failed setup can be retried.

The new audit changeset has a rollback; existing migrations are unchanged.

## Verification

With Java 21 and Docker available, run `./mvnw -Pintegration -Dit.test=UserManagementIT,PasswordRecoveryIT verify` from `backend`. Tests cover the current and legacy endpoints, 72-hour TTL, password violations, first login, trust scope, concurrent token use, CSRF, expiry, reuse, audit persistence and rollback. `PasswordPolicyTest` covers normalization and the encoder byte limit.
