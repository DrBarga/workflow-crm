# User management API

Only an active `SUPERADMIN` can manage users. Every operation reloads the caller's role and status from the database; role claims in an existing JWT do not override current permissions. Requests without a valid access cookie receive 401. Authenticated callers without permission receive a generic 403.

## Endpoints

- `GET /api/users?role=MANAGER&regionId=<uuid>&status=ACTIVE&page=0&size=20`: combined optional filters and pagination. Page starts at 0; size is 1..100. Results are ordered by creation time descending and ID for stable pagination.
- `GET /api/users/{id}`: retrieve one user.
- `POST /api/users`: create an inactive user and send one password-setup email. Required fields: `email`, `fullName` and `role`; `regionIds` contains the assigned region UUIDs; at least one is required for configured regional roles. The legacy `regionId` field accepts one region. Returns 201 and a Location header.
- `PATCH /api/users/{id}`: update any combination of `role`, `regionIds` and `status`. Omitted fields remain unchanged. An explicit null is accepted only for `regionId` when the resulting role permits it; other nulls, unknown fields and empty updates are rejected. An optional `version` detects changes since the client last loaded the user.
- `POST /api/users/{id}/password-invitation`: replace the current password invitation for an inactive user and send a new one. `POST /api/users/{id}/invitation` remains an alias for existing clients.
- `POST /api/user-invitations/open`: validate the password invitation and issue a device cookie without granting trust yet.
- `GET /api/auth/invite/{token}` and `POST /api/auth/set-password`: current invitation validation and initial password setup endpoints. See [initial password setup](initial-password-setup.md) for requests, validation and audit details.
- `POST /api/user-invitations/accept`: set the initial password using the one-time invitation token and trust the browser that submits it. If the browser did not call `/open`, the response issues its device cookie.
- `POST /api/auth/telegram/link`: initial binding only; a bound account returns 409. For self-service binding or replacement, use `POST /api/profile/telegram/link` with a ten-minute lifetime. See [profile Telegram binding](profile-telegram.md).
- `DELETE /api/users/{id}/telegram`: remove the user's Telegram binding and revoke their active sessions.
- There is no physical-delete endpoint. Deactivate with `{"status":"DISABLED"}`.

Create and update requests require the existing CSRF cookie and header from `GET /api/auth/csrf`. User responses never include password hashes or tokens. List and detail responses include `passwordSet`, `telegramLinked` and sorted `regionIds`. The compatibility `regionId` is the first UUID in that list, or null; it is not a separate assignment. Page responses contain `content`, `page`, `size`, `totalElements` and `totalPages`.

Email is trimmed and normalized to lowercase. Case-insensitive uniqueness is enforced by PostgreSQL, including concurrent requests; duplicates return 409 with a clear message. Missing users return 404; invalid input or unknown role/region references return 400. A stale version returns 409.

An administrator cannot deactivate themselves or remove their own administrator role. Updates lock the target row and change only requested fields. The user ID is preserved: existing object assignments are neither deleted nor reassigned, and object regions are not rewritten.

## Invitation and regional rules

The administrator never enters or sees a user's password or Telegram ID. New accounts remain `DISABLED` until the user follows the email invitation and sets a password. Password invitation tokens expire after 72 hours by default, are stored only as SHA-256 hashes and are single-use. Telegram binding uses a separate one-time token after sign-in and the user completes the binding in a private chat with the bot. A superadmin can remove a user's binding, but only the signed-in user can start a new one. Resending a password invitation invalidates its previous token. The administrator cannot activate an account while its initial password is still pending.

Opening the password link in the client calls `GET /api/auth/invite/{token}`, which validates the token and returns `PENDING_PASSWORD_SETUP` with a device cookie. The client obtains a CSRF token first and calls `POST /api/auth/set-password` with that cookie when the user submits a password. Only successful password setup grants device trust; opening the link alone does not. Trust belongs to the browser that submits the password, even if another browser opened the link first. The first password login on this browser issues a session without another challenge. Telegram can then be linked from the authenticated session. An untrusted browser receives a Telegram challenge if available, otherwise an email code.

`MANAGER`, `REGIONAL_MANAGER` and `CONTRACT_MANAGER` require at least one region by default. `CEO`, `DEVELOPMENT_DIRECTOR` and `SUPERADMIN` may have no region because their visibility is network-wide. The required role set is configured with `USER_REGION_REQUIRED_ROLES`; creation and every role/region update are checked against that configuration. Omitting both region fields during a patch preserves every assignment. `regionIds: []` clears assignments when the resulting role permits that. `regionIds: null` is rejected. `regionId: null` remains the compatibility form of clearing assignments. Supplying both fields during a patch is rejected; region lists reject null elements and deduplicate UUIDs. Filtering by `regionId` matches any assigned region without duplicating users.

Mail delivery uses the configured SMTP connection, `USER_INVITATION_MAIL_FROM` and `USER_INVITATION_URL`. SMTP failure does not roll back user creation: the delivery failure is logged without the token, and the administrator can resend the password invitation. An explicit retry reports a delivery error if SMTP is still unavailable.

## Change history

Creation and access changes write to `user_access_history` in the same transaction as the user change. Records contain actor/user IDs, before/after role, complete region lists and status, and a UTC timestamp. Failed and no-op updates do not create records. No password, token, email or request body is stored in this history. IDs intentionally have no cascading foreign keys, so future data-maintenance operations cannot erase the history.

The new Liquibase changeset has its own rollback. Applied migrations are unchanged. This is the user-management change history, not the separate full audit-log UI or retention-management feature.

## Verification

From `backend`, with Java 21 and Docker available:

```bash
./mvnw verify -Pintegration
```

`UserManagementIT` covers API authorization, filters, validation, duplicates, partial updates, self-protection, CSRF, change history, one-email creation, password invitation replacement, device trust after password setup, first login, post-login Telegram binding and SMTP recovery. Reference preservation and regional access are also checked against the production objects API. See [region model](user-region-model.md) and [OpenAPI](users.openapi.yaml).
