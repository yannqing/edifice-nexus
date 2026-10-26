# Optional Project Contract File

Back up the Edifice database, then apply `optional_project_contract_file.sql` before
deploying the backend. Do not run this migration on OA. The original schema makes
`contract.contract_file` NOT NULL; changing frontend validation alone is insufficient.

Only this column's nullability changes. Existing contracts, amounts, file references,
MinIO objects, and approval records are not rewritten. Re-running the SQL is safe.
Projects without a file still have a contract record and can receive a main file and
additional attachments later through project editing, with the existing manager/admin
permissions. Uploaded replacement files must belong to the current operator.

To roll back, retain the nullable schema. Do not restore NOT NULL or the previous
mandatory-file application until all missing contract files have been supplied.
