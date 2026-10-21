# Project File Drafts And Batch Applications

Apply `project_file_draft_batches.sql` once before deploying the matching backend
and frontend. Back up the database and retain the previous artifacts first.
This migration has not been applied to production by local development.

- A draft is a `project_files` row with status 0 and a fixed expiry date.
- Legacy status-0 rows without an expiry date remain visible as before and are
  not converted to private drafts or expired by the new cleanup job.
- The default retention is 30 days. Set `project-files.draft-retention-days` to 7
  for a seven-day policy. Edits and save retries do not extend the deadline.
- Saving uploaded files to drafts is explicit. An uploaded but unsaved file is
  not yet a project draft; the UI warns before closing that form.
- One save handles up to 20 files; one application can combine up to 200 live
  drafts owned by the same user in the same project, across dates/stages/types.
- A batch header has `batch_header=1` and a null `file_id`. Its member rows point
  to it via `batch_id`. Only the header owns an approval chain (`biz_type_ext=file`).
- A submitted attachment list is immutable. Approval/rejection/withdrawal update
  the entire batch in the same transaction. Only the current approver can act;
  the existing flow configuration, including self-approval policy, still applies.
- Statuses: 0 draft, 1 pending, 2 approved, 3 rejected, 4 withdrawn, 5 expired.
- Expiry is enforced synchronously on submission and asynchronously every hour.
  Only unsubmitted drafts expire. Expiry/removal is logical, not MinIO deletion;
  physical garbage collection requires a separate reference-aware retention job.
- Rejected/withdrawn applications may be copied to new drafts. The original
  file list and approval history are retained, including for historical single
  file approvals. Existing records are not merged or rewritten.
- Approval pages/statistics/count badges count applications, not attachments.
  Project file lists/archives/counts exclude headers and unsubmitted drafts.

Rollback: stop new submissions before reverting a release. Do not drop columns
or restore the old backend while new batch applications remain in use: it cannot
correctly handle batch headers. Keep the database and storage backup intact.
