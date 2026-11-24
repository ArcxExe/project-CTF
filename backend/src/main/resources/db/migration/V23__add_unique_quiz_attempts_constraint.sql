-- V23: Deduplicate quiz_attempts and enforce UNIQUE (quiz_id, student_id)

DELETE FROM quiz_attempts qa
USING (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY quiz_id, student_id
               ORDER BY
                   CASE WHEN status = 'COMPLETED' THEN 0 ELSE 1 END,
                   score DESC,
                   completed_at ASC NULLS LAST,
                   started_at ASC,
                   id ASC
           ) AS rn
    FROM quiz_attempts
) ranked
WHERE qa.id = ranked.id
  AND ranked.rn > 1;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_quiz_attempts_quiz_student'
          AND conrelid = 'quiz_attempts'::regclass
    ) THEN
        ALTER TABLE quiz_attempts
            ADD CONSTRAINT uq_quiz_attempts_quiz_student UNIQUE (quiz_id, student_id);
    END IF;
END $$;
