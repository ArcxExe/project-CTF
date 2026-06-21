CREATE TABLE promo_code_claims (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promo_code_id UUID NOT NULL REFERENCES promo_codes(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    claimed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT unique_promo_student UNIQUE (promo_code_id, student_id)
);

ALTER TABLE promo_codes
    ADD COLUMN max_uses INT NOT NULL DEFAULT 1,
    ADD COLUMN used_count INT NOT NULL DEFAULT 0;

-- Migrate existing data
INSERT INTO promo_code_claims (promo_code_id, student_id, claimed_at)
SELECT id, used_by_student_id, COALESCE(used_at, NOW())
FROM promo_codes
WHERE is_used = TRUE AND used_by_student_id IS NOT NULL;

UPDATE promo_codes
SET used_count = 1
WHERE is_used = TRUE AND used_by_student_id IS NOT NULL;

-- Drop obsolete columns
ALTER TABLE promo_codes
    DROP COLUMN is_used,
    DROP COLUMN used_by_student_id,
    DROP COLUMN used_at;
