CREATE TABLE academic_flows (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE academic_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    flow_id UUID REFERENCES academic_flows(id),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE students (
    id UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100),
    group_id UUID REFERENCES academic_groups(id),
    student_code VARCHAR(50) UNIQUE NOT NULL,
    user_id UUID UNIQUE REFERENCES users(id),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

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

CREATE TABLE promo_codes (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    modifier_type VARCHAR(30) NOT NULL,
    value NUMERIC(10,2),
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_by_student_id UUID REFERENCES students(id),
    activated_at TIMESTAMP
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
