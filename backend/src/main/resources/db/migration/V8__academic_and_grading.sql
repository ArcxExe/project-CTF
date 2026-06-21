CREATE TABLE academic_flows (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- Migrate existing academic_streams to academic_flows
INSERT INTO academic_flows (id, name, academic_year, created_at)
SELECT id, name, '2025/2026', NOW() FROM academic_streams;

-- Modify academic_groups to use flow_id instead of stream_id
ALTER TABLE academic_groups ALTER COLUMN name TYPE VARCHAR(50);
ALTER TABLE academic_groups ADD COLUMN flow_id UUID REFERENCES academic_flows(id);
UPDATE academic_groups SET flow_id = stream_id;
ALTER TABLE academic_groups DROP COLUMN stream_id;
DROP TABLE academic_streams;

-- Modify students to support first_name, last_name, middle_name, and status
ALTER TABLE students
    ADD COLUMN first_name VARCHAR(100) NOT NULL DEFAULT 'New',
    ADD COLUMN last_name VARCHAR(100) NOT NULL DEFAULT 'Student',
    ADD COLUMN middle_name VARCHAR(100),
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE students ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE students ALTER COLUMN student_code SET NOT NULL;

CREATE TABLE lab_scores (
    id UUID PRIMARY KEY,
    student_id UUID REFERENCES students(id) UNIQUE,
    score INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE lab_score_history (
    id UUID PRIMARY KEY,
    student_id UUID REFERENCES students(id),
    old_score INTEGER NOT NULL,
    new_score INTEGER NOT NULL,
    changed_by_user_id UUID,
    reason TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE manual_submissions (
    id UUID PRIMARY KEY,
    student_id UUID REFERENCES students(id),
    challenge_id UUID REFERENCES challenges(id),
    file_path VARCHAR(255),
    points_earned INTEGER,
    checked_by UUID,
    checked_at TIMESTAMP,
    percentage_multiplier INTEGER,
    status VARCHAR(30)
);

CREATE TABLE grading_scales (
    id UUID PRIMARY KEY,
    min_coefficient NUMERIC(3,2) NOT NULL,
    max_coefficient NUMERIC(3,2) NOT NULL,
    grade INTEGER NOT NULL,
    description VARCHAR(150)
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL,
    user_id UUID,
    username VARCHAR(100),
    role VARCHAR(30),
    action_type VARCHAR(50) NOT NULL,
    target_object VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    ip_address VARCHAR(45)
);
