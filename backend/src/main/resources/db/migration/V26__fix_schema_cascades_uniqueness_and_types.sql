-- V26: Fix missing ON DELETE CASCADE FKs, lab_scores.is_eligible default,
-- case-insensitive UNIQUE constraints on academic_flows.name and students.student_code,
-- V8 TIMESTAMP -> TIMESTAMPTZ columns, ctf_tasks.difficulty length, and quiz_attempts.status backfill (#30)

-- 1. Replace 7 foreign keys missing ON DELETE CASCADE (drop via catalog lookup to handle renamed tables/columns)
DO $$
DECLARE
    fk_rec RECORD;
    fk_spec RECORD;
BEGIN
    FOR fk_spec IN
        SELECT *
        FROM (VALUES
            ('quiz_attempts',      'quiz_id',      'fk_quiz_attempts_quiz_id',           'tests',     'id'),
            ('quiz_attempts',      'student_id',   'fk_quiz_attempts_student_id',        'students',  'id'),
            ('score_adjustments',  'student_id',   'fk_score_adjustments_student_id',    'students',  'id'),
            ('lab_scores',         'student_id',   'fk_lab_scores_student_id',           'students',  'id'),
            ('lab_score_history',  'student_id',   'fk_lab_score_history_student_id',    'students',  'id'),
            ('manual_submissions', 'student_id',   'fk_manual_submissions_student_id',   'students',  'id'),
            ('manual_submissions', 'challenge_id', 'fk_manual_submissions_challenge_id', 'ctf_tasks', 'id')
        ) AS t(tbl, col, new_name, ref_tbl, ref_col)
    LOOP
        IF to_regclass(fk_spec.tbl) IS NULL THEN
            CONTINUE;
        END IF;

        FOR fk_rec IN
            SELECT c.conname
            FROM pg_constraint c
            JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
            WHERE c.conrelid = fk_spec.tbl::regclass
              AND c.contype = 'f'
              AND array_length(c.conkey, 1) = 1
              AND a.attname = fk_spec.col
        LOOP
            EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', fk_spec.tbl, fk_rec.conname);
        END LOOP;

        EXECUTE format(
            'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %I(%I) ON DELETE CASCADE',
            fk_spec.tbl, fk_spec.new_name, fk_spec.col, fk_spec.ref_tbl, fk_spec.ref_col
        );
    END LOOP;
END $$;

-- 2. Align lab_scores.is_eligible default with LabScore entity (false)
ALTER TABLE lab_scores
    ALTER COLUMN is_eligible SET DEFAULT FALSE;

-- 3. Backfill quiz_attempts.status for legacy rows migrated in V12 where completed_at is already populated
UPDATE quiz_attempts
SET status = 'COMPLETED'
WHERE completed_at IS NOT NULL
  AND status = 'IN_PROGRESS';

-- 4. Align ctf_tasks.difficulty column length with CtfTask entity (@Column(length = 50))
ALTER TABLE ctf_tasks
    ALTER COLUMN difficulty TYPE VARCHAR(50);

-- 5. Convert V8 TIMESTAMP (without time zone) columns mapped to Java Instant into TIMESTAMPTZ
ALTER TABLE academic_flows
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT NOW();

ALTER TABLE lab_scores
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT NOW(),
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT NOW();

ALTER TABLE lab_score_history
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT NOW();

ALTER TABLE audit_logs
    ALTER COLUMN timestamp TYPE TIMESTAMPTZ USING timestamp AT TIME ZONE 'UTC',
    ALTER COLUMN timestamp SET DEFAULT NOW();

DO $$
BEGIN
    IF to_regclass('manual_submissions') IS NOT NULL THEN
        ALTER TABLE manual_submissions
            ALTER COLUMN checked_at TYPE TIMESTAMPTZ USING checked_at AT TIME ZONE 'UTC';
    END IF;
END $$;

-- 6. Deduplicate case-insensitive academic_flows.name (re-pointing groups/scales to the earliest flow) and enforce UNIQUE
WITH ranked_flows AS (
    SELECT id,
           FIRST_VALUE(id) OVER (PARTITION BY LOWER(TRIM(name)) ORDER BY created_at ASC, id ASC) AS keep_id,
           ROW_NUMBER() OVER (PARTITION BY LOWER(TRIM(name)) ORDER BY created_at ASC, id ASC) AS rn
    FROM academic_flows
),
dups AS (
    SELECT id AS dup_id, keep_id
    FROM ranked_flows
    WHERE rn > 1
)
UPDATE academic_groups g
SET flow_id = d.keep_id
FROM dups d
WHERE g.flow_id = d.dup_id;

WITH ranked_flows AS (
    SELECT id,
           FIRST_VALUE(id) OVER (PARTITION BY LOWER(TRIM(name)) ORDER BY created_at ASC, id ASC) AS keep_id,
           ROW_NUMBER() OVER (PARTITION BY LOWER(TRIM(name)) ORDER BY created_at ASC, id ASC) AS rn
    FROM academic_flows
),
dups AS (
    SELECT id AS dup_id, keep_id
    FROM ranked_flows
    WHERE rn > 1
)
UPDATE grading_scales gs
SET flow_id = d.keep_id
FROM dups d
WHERE gs.flow_id = d.dup_id
  AND NOT EXISTS (SELECT 1 FROM grading_scales existing WHERE existing.flow_id = d.keep_id);

WITH ranked_flows AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY LOWER(TRIM(name)) ORDER BY created_at ASC, id ASC) AS rn
    FROM academic_flows
)
DELETE FROM academic_flows f
USING ranked_flows r
WHERE f.id = r.id
  AND r.rn > 1;

UPDATE academic_flows
SET name = TRIM(name)
WHERE name <> TRIM(name);

CREATE UNIQUE INDEX IF NOT EXISTS uq_academic_flows_name_lower
    ON academic_flows (LOWER(name));

-- 7. Disambiguate any legacy case-insensitive duplicate students.student_code and enforce UNIQUE on LOWER(student_code)
WITH ranked_students AS (
    SELECT id,
           student_code,
           ROW_NUMBER() OVER (
               PARTITION BY LOWER(TRIM(student_code))
               ORDER BY (user_id IS NULL) ASC, created_at ASC, id ASC
           ) AS rn
    FROM students
)
UPDATE students s
SET student_code = LEFT(TRIM(r.student_code), 40) || '-D' || r.rn || '-' || LEFT(REPLACE(s.id::text, '-', ''), 4)
FROM ranked_students r
WHERE s.id = r.id
  AND r.rn > 1;

UPDATE students
SET student_code = TRIM(student_code)
WHERE student_code <> TRIM(student_code);

CREATE UNIQUE INDEX IF NOT EXISTS uq_students_student_code_lower
    ON students (LOWER(student_code));

-- 8. Index students.token_info case-insensitively for registration token lookup
CREATE INDEX IF NOT EXISTS idx_students_token_info_lower
    ON students (LOWER(token_info))
    WHERE token_info IS NOT NULL;
