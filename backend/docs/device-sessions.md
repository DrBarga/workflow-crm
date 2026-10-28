# Device sessions

Active device sessions are stored in Redis and remain part of the authentication security boundary. The registry never returns access tokens, refresh tokens, the server-side device digest, or Redis keys.

## User operations

`GET /api/auth/devices` returns the authenticated user's active sessions, newest login first. Each item contains:

- `id`: the opaque session identifier used by the revocation endpoint;
- `userAgent`: the bounded user agent recorded by the server during login;
- `ipAddress`: the direct remote address observed by the application;
- `lastLoginAt`: the UTC timestamp when that session was created;
- `current`: whether the item belongs to the access token used for the request.

`DELETE /api/auth/devices/{sessionId}` revokes only the matching session when it belongs to the authenticated user. An unknown, malformed, expired, or foreign session identifier returns `404` without revealing whether another user's session exists. Revoking the current session also clears the access and refresh cookies. Other sessions remain active.

## Superadmin operations

`DELETE /api/users/{userId}/devices/{sessionId}` revokes one session of the selected user. `DELETE /api/users/{userId}/devices` revokes all current sessions of that user. Both operations reload the administrator from the database and require an active `SUPERADMIN` account, so a stale JWT cannot preserve removed privileges.

All delete requests are protected by the existing CSRF cookie and header flow. A session revocation takes effect for both access and refresh tokens on the next request. Redis failures fail closed and cannot produce a successful revocation response.

Blocked devices and temporary login locks have separate [administrative inspection and unlock endpoints](admin-security-unlock.md). Revoking a session does not remove a device block; unblocking a device does not restore its sessions or trust.
