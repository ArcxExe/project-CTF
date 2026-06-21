CREATE TABLE IF NOT EXISTS grading_scales (
    id UUID PRIMARY KEY,
    min_coefficient NUMERIC(3,2) NOT NULL,
    max_coefficient NUMERIC(3,2) NOT NULL,
    grade INTEGER NOT NULL,
    description VARCHAR(150)
);
