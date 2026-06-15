# Gougu OA Local Development

This directory contains the Gougu OA source currently integrated with Edifice.
Production runtime data and secrets are intentionally excluded from Git.

## Requirements

- Docker Desktop with Docker Compose
- MySQL 8 with an `office_db` database
- A running Edifice backend for user, permission, contract, and SSO integration

## Setup

1. Create the local environment file:

   ```bash
   cp apps/gougu-oa/.env.example apps/gougu-oa/.env
   ```

2. Set the same values for `EDIFICE_SSO_SECRET` and the Edifice backend
   `oa.sso-secret`. Set `EDIFICE_SYNC_KEY` / `EDIFICE_SYNC_API_KEY` to the
   Edifice backend `oa.sync.api-key`. A matching Edifice template is available
   at `apps/edifice-core/.env.example`.

   OA runs inside Docker, so services running directly on the Mac are reached
   through `host.docker.internal`.
   For local cross-system SSO, keep `EDIFICE_WEB_URL=http://127.0.0.1:3000`
   in `apps/gougu-oa/.env` and `OA_URL=http://127.0.0.1:8080` in
   `apps/edifice-core/.env`. Production should provide its own public URLs
   through the same environment keys.

3. Initialize `office_db` using:

   ```bash
   mysql -u root -p office_db < app/install/data/gouguoa.sql
   mysql -u root -p < ../../database/migrations/v2/gougu_oa_local_integration.sql
   ```

   Apply Edifice migrations separately to `edifice_db`, including
   `edifice_menu_permissions.sql` and `oa_contract_project_mapping.sql`.

4. From the repository root, start OA only:

   ```bash
   pnpm dev:oa
   ```

Open `http://127.0.0.1:8080/home/index/index.html`.

Use `pnpm dev` from the repository root to start Edifice Vision, Edifice Core,
and Gougu OA together.

The first OA start builds the PHP 8.2 development image and installs Composer
dependencies into a Docker volume. Use `pnpm dev:oa:down` to stop and remove the
OA container, or `pnpm dev:oa:logs` to follow its logs.

## Repository Rules

- Do not commit `.env`, `vendor`, `runtime`, uploaded files, database dumps,
  backups, or logs.
- OA owns employee, department, position, and Edifice permission maintenance.
- The original OA project module is hidden. Engineering projects are created
  from approved sales contracts and managed in Edifice.
