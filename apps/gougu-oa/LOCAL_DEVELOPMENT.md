# Gougu OA Local Development

This directory contains the Gougu OA source currently integrated with Edifice.
Production runtime data and secrets are intentionally excluded from Git.

## Requirements

- PHP 8.1 or newer
- Composer
- MySQL 8 with an `office_db` database
- PHP extensions required by `composer.json`, including PDO MySQL and cURL
- A running Edifice backend for user, permission, contract, and SSO integration

## Setup

1. Install PHP dependencies:

   ```bash
   cd apps/gougu-oa
   composer install
   ```

2. Create the local environment file:

   ```bash
   cp .env.example .env
   ```

3. Set the same values for `EDIFICE_SSO_SECRET` and the Edifice backend
   `oa.sso-secret`. Set `EDIFICE_SYNC_KEY` / `EDIFICE_SYNC_API_KEY` to the
   Edifice backend `oa.sync.api-key`. A matching Edifice template is available
   at `apps/edifice-core/.env.example`.

4. Initialize `office_db` using:

   ```bash
   mysql -u root -p office_db < app/install/data/gouguoa.sql
   mysql -u root -p < ../../database/migrations/v2/gougu_oa_local_integration.sql
   ```

   Apply Edifice migrations separately to `edifice_db`, including
   `edifice_menu_permissions.sql` and `oa_contract_project_mapping.sql`.

5. Ensure these writable directories exist:

   ```bash
   mkdir -p runtime public/storage public/backup
   ```

6. Start OA only:

   ```bash
   php think run --host 127.0.0.1 --port 8080
   ```

Open `http://127.0.0.1:8080/home/index/index.html`.

## Repository Rules

- Do not commit `.env`, `vendor`, `runtime`, uploaded files, database dumps,
  backups, or logs.
- OA owns employee, department, position, and Edifice permission maintenance.
- The original OA project module is hidden. Engineering projects are created
  from approved sales contracts and managed in Edifice.
