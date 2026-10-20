-- =====================================================================================================================
-- PureEats: delete ALL data except one super admin account.
--
-- !!! IRREVERSIBLE. Run it through scripts/db/reset-to-superadmin.sh, which takes a pg_dump backup first. !!!
--
-- Empties every table in the public schema, in ONE transaction (any error = nothing is deleted), except:
--   * users                  - only the super admin row (email below) stays; its address / delivery-partner links are cleared
--   * model_has_roles        - only that user's role rows stay
--   * model_has_permissions  - only that user's direct permission rows stay
--   * roles, permissions, role_has_permissions, orderstatuses - reference data the app needs to run
--   * with keep_config=true: settings, payment_gateways, sms_gateways, modules, translations, pages (app configuration:
--     payment keys, maps / Firebase keys, CMS pages...). Default false = those are emptied too.
-- Large objects (the oid/@Lob text columns) only referenced by deleted rows are unlinked too.
-- Uploaded files on disk (pureeats.media.local.base-dir) are NOT touched.
--
-- Usage (normally via the .sh wrapper):
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v confirm=DELETE_ALL_DATA \
--        [-v superadmin_email=superadmin@pureeats.in] [-v keep_config=true] -f reset-to-superadmin.sql
-- =====================================================================================================================

\if :{?superadmin_email}
\else
  \set superadmin_email 'superadmin@pureeats.in'
\endif
\if :{?keep_config}
\else
  \set keep_config false
\endif
\if :{?confirm}
\else
  \set confirm ''
\endif

SELECT (:'confirm' = 'DELETE_ALL_DATA') AS confirmed \gset
\if :confirmed
\else
  \echo 'Refusing to run: pass -v confirm=DELETE_ALL_DATA to delete all data except the super admin.'
  \quit
\endif

BEGIN;

SELECT set_config('pureeats.superadmin_email', :'superadmin_email', true),
       set_config('pureeats.keep_config', :'keep_config', true);

DO $reset$
DECLARE
    admin_email text := current_setting('pureeats.superadmin_email');
    keep_config boolean := current_setting('pureeats.keep_config')::boolean;
    admin_id bigint;
    matches int;
    -- Kept whole: reference data (+ configuration when keep_config).
    keep_whole text[] := ARRAY['roles', 'permissions', 'role_has_permissions', 'orderstatuses'];
    -- Kept, but only the super admin's rows.
    keep_filtered text[] := ARRAY['users', 'model_has_roles', 'model_has_permissions'];
    to_truncate text;
    unlinked int := 0;
BEGIN
    IF keep_config THEN
        keep_whole := keep_whole || ARRAY['settings', 'payment_gateways', 'sms_gateways', 'modules', 'translations', 'pages'];
    END IF;

    -- 1. Exactly one account with that email, and it must hold the Super Admin role - otherwise stop, delete nothing.
    SELECT count(*) INTO matches FROM users WHERE lower(email) = lower(admin_email);
    IF matches <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one user with email %, found % - nothing was deleted.', admin_email, matches;
    END IF;
    SELECT id INTO admin_id FROM users WHERE lower(email) = lower(admin_email);
    IF NOT EXISTS (
        SELECT 1 FROM model_has_roles m JOIN roles r ON r.id = m.role_id
        WHERE m.model_id = admin_id AND r.name IN ('Super Admin', 'SUPER_ADMIN')
    ) THEN
        RAISE EXCEPTION 'User % (id %) is not a Super Admin - nothing was deleted.', admin_email, admin_id;
    END IF;
    RAISE NOTICE 'Keeping super admin % (id %). keep_config=%', admin_email, admin_id, keep_config;

    -- 2. Remember which large objects the rows we keep still point at, before anything goes.
    CREATE TEMP TABLE kept_lobs (lob oid PRIMARY KEY) ON COMMIT DROP;
    DECLARE
        col record;
    BEGIN
        FOR col IN
            SELECT c.table_name, c.column_name
            FROM information_schema.columns c
            WHERE c.table_schema = 'public' AND c.data_type = 'oid'
              AND (c.table_name = ANY (keep_whole) OR c.table_name = ANY (keep_filtered))
        LOOP
            IF col.table_name = 'users' THEN
                EXECUTE format('INSERT INTO kept_lobs SELECT %I FROM %I WHERE id = $1 AND %I IS NOT NULL ON CONFLICT DO NOTHING',
                               col.column_name, col.table_name, col.column_name) USING admin_id;
            ELSE
                EXECUTE format('INSERT INTO kept_lobs SELECT %I FROM %I WHERE %I IS NOT NULL ON CONFLICT DO NOTHING',
                               col.column_name, col.table_name, col.column_name);
            END IF;
        END LOOP;
    END;

    -- 3. Empty every other table in one statement, resetting their id sequences (the schema has no foreign keys).
    SELECT string_agg(format('%I', t.table_name), ', ' ORDER BY t.table_name) INTO to_truncate
    FROM information_schema.tables t
    WHERE t.table_schema = 'public' AND t.table_type = 'BASE TABLE'
      AND NOT (t.table_name = ANY (keep_whole)) AND NOT (t.table_name = ANY (keep_filtered));
    IF to_truncate IS NOT NULL THEN
        RAISE NOTICE 'Emptying: %', to_truncate;
        EXECUTE 'TRUNCATE TABLE ' || to_truncate || ' RESTART IDENTITY';
    END IF;

    -- 4. Keep only the super admin in the filtered tables.
    DELETE FROM users WHERE id <> admin_id;
    UPDATE users SET default_address_id = NULL, delivery_guy_detail_id = NULL WHERE id = admin_id;
    DELETE FROM model_has_roles WHERE model_id <> admin_id;
    IF to_regclass('public.model_has_permissions') IS NOT NULL THEN
        DELETE FROM model_has_permissions WHERE model_id <> admin_id;
    END IF;

    -- 5. Free the large objects nothing points at any more.
    SELECT count(lo_unlink(m.oid)) INTO unlinked
    FROM pg_largeobject_metadata m
    WHERE NOT EXISTS (SELECT 1 FROM kept_lobs k WHERE k.lob = m.oid);
    RAISE NOTICE 'Unlinked % large object(s).', unlinked;
END
$reset$;

COMMIT;

SELECT id, email, name FROM users;
\echo 'Done: only the super admin above is left. Restart the backend so caches start empty.'
