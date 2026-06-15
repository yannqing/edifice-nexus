-- Local-safe Gougu OA integration settings.
-- Run after apps/gougu-oa/app/install/data/gouguoa.sql has initialized office_db.

SET NAMES utf8mb4;

-- Hide Gougu OA's collaboration project module; engineering projects live in Edifice.
UPDATE office_db.oa_admin_rule
SET menu = 0, update_time = UNIX_TIMESTAMP()
WHERE module = 'project';

UPDATE office_db.oa_mobile_bar
SET status = 0, update_time = UNIX_TIMESTAMP()
WHERE url LIKE '%project%';

-- Add an idempotent OA workbench entry. The target signs a short-lived SSO token.
INSERT INTO office_db.oa_links
    (title, logo, url, target, sort, create_time, update_time, delete_time)
SELECT
    'Edifice 工程管理', 0, '/home/index/edifice_sso', 1, 100,
    UNIX_TIMESTAMP(), UNIX_TIMESTAMP(), 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM office_db.oa_links
    WHERE title = 'Edifice 工程管理' AND delete_time = 0
);

UPDATE office_db.oa_links
SET url = '/home/index/edifice_sso',
    target = 1,
    sort = 100,
    update_time = UNIX_TIMESTAMP(),
    delete_time = 0
WHERE title = 'Edifice 工程管理';
