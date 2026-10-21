# Independent Settlement Projects

Apply `settlement_projects.sql` once, after a database backup, before deploying
the matching backend. It adds one nullable relation and an index; existing IDs,
contracts, stages, approvals, files and financial records are not rewritten.

## Business Boundaries

- A root project can contain independent settlement projects, one level deep.
- Creation is available in the root project's detail dialog, under the
  settlement-projects tab (separate from the existing overview). Only the root
  project's manager or an administrator with all-projects permission can create.
- Each child uses the existing project lifecycle: its own contract/base/benefit
  amounts, type-derived stages, members, inspections, files, output distributions,
  benefit corrections, collections and archive. No amount is derived from or
  limited by the parent's amounts. Its stage completion never advances a parent
  or sibling stage.
- The root's original business records remain intact. A root can still have its
  own work; existing work is not implicitly transferred into a child.
- The child summary sums child contracts only, not the parent. It is a separate
  display, not an additional accounting entry. Existing reports still count each
  project's own records once; they do not add this summary to the parent.
- Membership is independent. The creation form preselects the parent's members,
  but the creator can change them. Parent membership does not grant access to a
  child's files, inspections or finances. Lists and totals include only children
  visible to the current user (or all children for existing global-view roles).
- Child details show a link to the parent only when that user may view it.
- Children remain individually available in project, inspection and output
  selectors. My projects/all projects can filter root vs settlement projects.
- A root with live children cannot be deleted. A root cannot be archived until
  every child is archived. Neither operation cascades. Unarchive the parent
  before reopening a child. Creation/deletion/archive serialize on the parent.
- My-projects managers may start/restart their own project stages; project IDs
  are checked server-side and a batch cannot mix stages from different projects.
  Settlement inspections require that child's manager or a global project
  administrator, not merely membership of the parent.
- Existing projects cannot be silently reparented through update/import APIs.
  Historical data reassignment requires a separate reviewed migration.

Project/child search and archive/level filters are applied before SQL pagination.
The frontend uses the server's total rather than the current page's row count.

## Verification And Rollback

`SettlementProjectTests` uses real SQL, pagination, row locking and transaction
rollback, exercising child isolation, scoped sums, creation rights and lifecycle
guards. External services (e.g. approval routing) use test doubles.

Before release, verify the migration against the target MySQL version and test a
disposable parent/A1/A2 lifecycle. Deploy backend and frontend together. No
production data or configuration changes are performed by these local changes.

The previous application cannot display/manage parent-child associations.
Revert artifacts only after stopping child creation and reviewing any new child
activity. Keep the column and its data; do not drop relations or delete children
as a rollback shortcut. Physical files and original business rows remain intact.
