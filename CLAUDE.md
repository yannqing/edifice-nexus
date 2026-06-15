# CLAUDE.md

This repository uses `AGENTS.md` as the source of truth for coding-agent instructions.

Before making changes, read `AGENTS.md` and follow the same constraints:

- Keep server and local git state synchronized.
- Never commit secrets or environment-specific files.
- Preserve the current Gougu OA SSO path: `/home/sso/login?ssoToken=...`.
- Treat OA as the authority for users, departments, positions, and permission configuration.

