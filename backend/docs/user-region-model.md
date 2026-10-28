# Users, roles and regions

`users` owns account details and `role_id`. Region assignments are stored only
in `user_regions`; `(user_id, region_id)` is unique and both references have
foreign keys. Users can have several assignments or none when their role allows
it. Changing assignments does not change the region or manager of any object.

`regions` is a flat directory with `id`, unique `code`, `name` and entity metadata.
There is no parent field or inherited regional access. Managers and regional
managers are checked against all explicit assignments on each request. Object
ownership and other role restrictions still apply. Removing an assignment takes
effect for existing sessions; a JWT region claim cannot restore revoked access.

The six role UUIDs and codes remain stable. Seeded display names are:

| Code | Name |
| --- | --- |
| MANAGER | Менеджер |
| REGIONAL_MANAGER | Провідний менеджер регіону |
| DEVELOPMENT_DIRECTOR | Директор департаменту розвитку |
| CEO | CEO |
| CONTRACT_MANAGER | Погодження договорів |
| SUPERADMIN | Адміністратор |

No production regions are invented or seeded. The official Workflow directory is
still required for initial directory population. Existing directory rows and
assignments remain intact. The opt-in local mail-test seed is development data.

## Upgrade and rollback

`20261015-flat-user-regions` is appended after existing migrations. It merges
legacy `users.region_id` values into `user_regions` without replacing additional
assignments, then removes that column and `regions.parent_id`. Account details,
password hashes, statuses, region IDs, boundaries and object references remain
unchanged. No descendant access is converted into extra assignments: only an
administrator can grant regional access explicitly.

`user_region_model_legacy` holds the migration snapshot of former single-region
values, parent links and role labels for rollback. It is historical data and is
never consulted by runtime authorization. Access history stores the complete
before/after UUID arrays; old scalar audit fields remain for historical readers.

Rollback restores the old columns, parent links and role labels. Every join-table
assignment and access-history array is retained. The old single-region field
uses the former value only if that assignment still exists; otherwise it uses
the first remaining UUID. Reapplying the migration does not restore revoked
assignments or duplicate links. An old application can display only one of the
retained assignments; rollback does not make that application support multiple
regions.

Run the migration together with this backend update while old application
instances are stopped: old binaries still query the removed columns. The schema
rollback requires rolling back the application version as well. This change
does not deploy to an existing environment.
