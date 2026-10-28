# Administrative security unlock

An active superadmin can inspect a user's device blocks and temporary login lock. Every operation reloads the administrator's role and account status from the database. Revoked or expired authentication is rejected by the existing session filter. User IDs and block IDs are UUIDs; malformed parameters return `400`.

## Endpoints

The machine-readable contract is [OpenAPI 3.0](admin-security-unlock.openapi.json).

All routes require authentication. POST requests also require the CSRF cookie and header obtained from `GET /api/auth/csrf`.

Unlock actions share a limit per administrator: `ADMIN_SECURITY_MAX_ACTIONS=10` in `ADMIN_SECURITY_RATE_WINDOW=1m` by default. Both variables are optional, validated (positive count, window at least one second), and forwarded by local/dev Compose. The fixed window starts with the first accepted action attempt; rejected requests do not extend it. Reads do not consume this limit.

| Method | Route | Successful response |
| --- | --- | --- |
| GET | `/api/users/{userId}/blocked-devices` | `200`, array of blocked devices |
| POST | `/api/users/{userId}/blocked-devices/{blockId}/unblock` | `204`, no body |
| GET | `/api/users/{userId}/login-lock` | `200`, current lock state and shared settings |
| POST | `/api/users/{userId}/login-lock/unlock` | `204`, no body |

A blocked-device item contains `id`, `userAgent`, `ipAddress` and `blockedAt`. The ID is an opaque UUID for this particular block. It is different from the browser's device cookie, internal device digest and active session ID. Those security identifiers are never returned. The user agent and direct remote IP are captured by the backend when Telegram rejects the login. `blockedAt` is an ISO 8601 UTC timestamp. Results are ordered by that timestamp, newest first; an empty list is `[]`.

Older blocks remain visible and receive an opaque ID on first inspection without extending their lifetime. Their unavailable metadata is represented by empty strings and a null `blockedAt`. A new block of the same device receives a new ID, so a stale unblock request cannot remove it.

Example login-lock response:

```json
{
  "blocked": true,
  "failedAttempts": 5,
  "maxAttempts": 5,
  "lockDurationSeconds": 900,
  "blockedUntil": "2026-10-01T10:15:00Z"
}
```

`blockedUntil` is null when the account is not temporarily locked. The failed-attempt counter is shared by all devices using the normalized email address. The existing settings `LOGIN_MAX_ATTEMPTS=5` and `LOGIN_LOCK_DURATION=15m` are forwarded by both local and dev Compose configurations. Changing them changes the common login policy; a backend restart applies the configuration. Each failed attempt refreshes the counter's TTL; blocked requests do not extend it. Invalid settings prevent startup.

## Effects and errors

Unblocking a device revokes any remaining sessions of that device before removing its block. It removes any old trust marker and first-login grant. It does not create a session or trust the device: the next login follows the normal Telegram/email confirmation path. Other devices and users remain unchanged.

Unlocking login clears the temporary lock and failure counter. It does not enable a disabled account, change a password, alter device trust or remove device blocks.

| Status | Meaning |
| --- | --- |
| `401` | Authentication is missing, invalid, expired or revoked |
| `403` | The caller is not an active superadmin, or CSRF validation failed |
| `404` | The user or selected block does not exist; foreign block IDs also receive this response |
| `409` | Login is not temporarily locked, or the block changed while the operation was running |
| `429` | The administrator's unlock-action limit was reached (`REQUEST_RATE_LIMITED`) |
| `503` | Security storage or the audit journal is unavailable; reload the state before retrying |

Repeated device unblock requests return `404` after the block has been removed. Repeated login unlock requests return `409`. Unsuccessful permission checks and invalid selections do not create unlock journal entries.

## Journal

The audit module stores `security_unlock_events` in PostgreSQL. Each event records its UUID, administrator UUID, target user UUID, action (`DEVICE_UNBLOCK` or `LOGIN_UNLOCK`), optional opaque blocked-device UUID, outcome, and UTC creation/update timestamps. Account identifiers are retained as historical references without foreign keys, consistent with the existing audit tables; deleting an account must not erase audit history. Device cookies, device digests, passwords, tokens, email addresses and request bodies are not recorded.

The request is committed as `REQUESTED` before Redis is changed. A journal write failure at this stage prevents unblocking. Atomic Redis operations then remove only the selected current block or a currently locked failure counter. The journal is completed as `APPLIED`, `UNCHANGED`, or `FAILED`; only `APPLIED` receives `204`.

PostgreSQL and Redis do not share a transaction. A crash or a journal-result write failure after Redis changes leaves a durable `REQUESTED` entry and no successful acknowledgment. Inspect the current lock and this entry before retrying; `REQUESTED` must not be interpreted as a completed unblock. Migration `20261001_security_unlock_events.yaml` includes rollback.

## Verification

With Docker available, run from `backend`:

```bash
./mvnw verify -Pintegration -Dit.test=AdminSecurityIT,AuthLoginIT,DeviceSessionIT
```

The integration suite uses real PostgreSQL and Redis with test email/Telegram senders. It verifies Telegram rejection, listing, early unlock, old-token revocation, repeat requests, stale/foreign IDs, current database permissions, CSRF, shared defaults and journal failures. The frontend can use these routes in the user administration screen; this backend change does not add a new client screen.

| Task criterion | Verification |
| --- | --- |
| Inspect and unblock a user's devices | `AdminSecurityIT.listsRejectedDevicesAndUnblocksWithoutRestoringTrustOrSessions` and legacy/stale/foreign-block cases |
| Remove a temporary login lock early | `AdminSecurityIT.removesTemporaryLockEarlyAndRestartsFailureCounter` and disabled-account isolation |
| Shared duration, five attempts and 15-minute default | Actual login attempts and lock response in `AdminSecurityIT`; override and invalid-configuration cases in `LoginProtectionPropertiesTest` |
| Journal unlock operations | PostgreSQL event assertions and journal-write failure scenarios in `AdminSecurityIT`; Redis failure/race outcomes in `AdminSecurityServiceTest` |
