-- V19: Fix schema constraints, foreign keys, and missing indexes

-- [CRIT-6] Allow file-upload and choice/sequence attempts without a submitted flag
ALTER TABLE attempts ALTER COLUMN submitted_flag DROP NOT NULL;

-- [CRIT-4] Support percentage ranges up to 100.00 and flow-specific grading scales
ALTER TABLE grading_scales
    ALTER COLUMN min_coefficient TYPE NUMERIC(5,2),
    ALTER COLUMN max_coefficient TYPE NUMERIC(5,2);

ALTER TABLE grading_scales
    ADD COLUMN IF NOT EXISTS flow_id UUID REFERENCES academic_flows(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS grade_name VARCHAR(64);

-- [HIGH-11] Fix FK constraints to prevent 500 when deleting flows or competitions
ALTER TABLE academic_groups DROP CONSTRAINT IF EXISTS academic_groups_flow_id_fkey;
ALTER TABLE academic_groups
    ADD CONSTRAINT academic_groups_flow_id_fkey
    FOREIGN KEY (flow_id) REFERENCES academic_flows(id) ON DELETE SET NULL;

ALTER TABLE score_adjustments DROP CONSTRAINT IF EXISTS score_adjustments_competition_id_fkey;
ALTER TABLE score_adjustments
    ADD CONSTRAINT score_adjustments_competition_id_fkey
    FOREIGN KEY (competition_id) REFERENCES competitions(id) ON DELETE SET NULL;

-- [LOW-3] Drop orphaned audit_events table replaced by audit_logs in V8
DROP TABLE IF EXISTS audit_events;

-- [LOW-3] Demo student name update: full_name 'Student New' -> 'Иванов Иван Иванович' (fields: last_name, first_name, middle_name)
-- UPDATE students SET full_name = 'Иванов Иван Иванович' WHERE student_code = 'STU-0001' AND full_name = 'Student New';
UPDATE students
SET last_name = 'Иванов',
    first_name = 'Иван',
    middle_name = 'Иванович'
WHERE student_code = 'STU-0001';

-- [LOW-3] Create missing indexes
CREATE INDEX IF NOT EXISTS idx_academic_groups_flow_id ON academic_groups(flow_id);
CREATE INDEX IF NOT EXISTS idx_lab_score_history_student_id ON lab_score_history(student_id);
CREATE INDEX IF NOT EXISTS idx_quiz_attempts_student_id ON quiz_attempts(student_id);
CREATE INDEX IF NOT EXISTS idx_score_adjustments_student_id ON score_adjustments(student_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp);
