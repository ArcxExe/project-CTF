ALTER TABLE quiz_submissions RENAME TO quiz_attempts;

ALTER TABLE quiz_attempts RENAME COLUMN test_id TO quiz_id;

ALTER TABLE quiz_attempts RENAME COLUMN submitted_at TO completed_at;

ALTER TABLE quiz_attempts DROP COLUMN is_active;

ALTER TABLE quiz_attempts ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS';
