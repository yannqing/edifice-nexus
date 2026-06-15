# AGENTS.md

## Project Notes

- Work on the `dev` branch unless the user explicitly asks for another branch.
- The server at `211.149.166.182` may contain urgent direct edits. Before local work, compare `git status`, `git rev-parse HEAD`, and key diffs between local and server.
- Do not commit environment files, database dumps, logs, build output, macOS resource fork files (`._*`), or secrets. Runtime values belong in `.env` files or server process config, not git.
- Keep changes scoped. Do not reset or overwrite user/server changes unless explicitly requested.

## Runtime Layout

- `apps/edifice-core` is the Spring Boot backend.
- `apps/edifice-vision` is the Next.js frontend.
- `apps/gougu-oa` is the integrated Gougu OA/ThinkPHP source. The current server deployment still runs from `/root/project/workspace/office` until deployment is switched to this monorepo path.
- Edifice and OA share user/organization data through the existing sync code and database mappings. OA is the main maintenance entry for users, departments, positions, and permissions.

## OA Integration

- Current Gougu OA SSO entry is `/home/sso/login?ssoToken=...`.
- OA to Edifice reverse SSO entry is `/home/index/edifice_sso`.
- Do not use the old `/login/sso` route; it belongs to the previous integration path and returns a ThinkPHP 404 on the current OA app.
- Edifice permissions are configured from OA. Edifice should consume permission snapshots and hide/show menus accordingly, not expose a separate permission configuration UI.
- Default Edifice menus expected for normal permission sets include workbench, my projects, and personal performance unless the business rule changes.

## Verification

- For frontend changes, run TypeScript checks for `apps/edifice-vision` when practical.
- For backend changes, run Maven compile/tests for `apps/edifice-core` when practical.
- For OA changes, run PHP syntax checks and keep `apps/gougu-oa/.env`, `vendor`, `runtime`, uploads, and backups out of Git.
- After deployment-related changes, verify the exact running port and process before assuming a URL maps to dev or packaged output.
