ALTER TABLE challenges
    ADD COLUMN difficulty_level VARCHAR(30),
    ADD COLUMN max_score INT NOT NULL DEFAULT 100,
    ADD COLUMN is_first_blood_only BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN task_type VARCHAR(50) NOT NULL DEFAULT 'FLAG',
    ADD COLUMN attachments JSONB;

ALTER TABLE attempts
    ADD COLUMN file_path VARCHAR(512),
    ADD COLUMN score_awarded INT;

ALTER TABLE competitions
    ADD COLUMN sum_test_points BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN leaderboard_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN hidden_student_ids JSONB;

CREATE TABLE quiz_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id UUID REFERENCES tests(id) ON DELETE CASCADE,
    type VARCHAR(50),
    text TEXT NOT NULL,
    points INT NOT NULL,
    ordering INT NOT NULL
);

CREATE TABLE quiz_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID REFERENCES quiz_questions(id) ON DELETE CASCADE,
    option_text TEXT,
    is_correct BOOLEAN NOT NULL,
    sequence_order INT
);

CREATE TABLE quiz_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id UUID REFERENCES tests(id),
    student_id UUID REFERENCES students(id),
    started_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    score INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE promo_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    modifier_type VARCHAR(50) NOT NULL,
    value INT NOT NULL,
    is_used BOOLEAN NOT NULL DEFAULT FALSE,
    used_by_student_id UUID REFERENCES students(id),
    used_at TIMESTAMPTZ
);

CREATE TABLE score_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID REFERENCES students(id),
    competition_id UUID REFERENCES competitions(id),
    points INT NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
