# Project File Folders

Apply `project_file_folders.sql` to the Edifice database before starting the new backend.
This is a manual, one-time migration. Back up the database first; do not run it on OA.

- Existing `project_files.folder_id` values remain NULL (the root directory).
- Directories are project-scoped metadata, not approval requests or MinIO objects.
- File contents, MinIO keys, drafts, expiration dates, and existing approval chains are not rewritten.
- A folder upload creates/reuses directory paths, then saves files as ordinary drafts in groups of 20.
- Drafts from multiple directories can still be submitted together, up to 200 per application.
- Folder creation is restricted to project members or file administrators; archived projects are read-only.
- Limits: 128 characters per folder name, 20 levels, 5000 folders per project, 200 paths per request.
- Browser directory selection includes files only; empty directories must be created manually.

For rollback, first restore the previous application version. Preserve this schema and its metadata if
possible. Dropping the folder column/table loses classification but must never delete physical files.

Local checks:

- Backend: `mvn -Dtest='*,!EdificeCoreApplicationTests' test` from `apps/edifice-core`.
- Frontend: `pnpm exec tsc --noEmit` and `pnpm run test:folders` from `apps/edifice-vision`.
- The native TypeScript helper tests require Node.js 22.6 or newer.
