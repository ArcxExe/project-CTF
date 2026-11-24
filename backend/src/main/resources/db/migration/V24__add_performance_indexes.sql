-- V24: Add performance indexes for high-concurrency CTF, Quiz, and PromoCode queries

CREATE INDEX IF NOT EXISTS idx_attempts_task_student_correct ON attempts(task_id, student_id, is_correct);
CREATE INDEX IF NOT EXISTS idx_attempts_task_correct_submitted ON attempts(task_id, is_correct, submitted_at);
CREATE INDEX IF NOT EXISTS idx_attempts_student_submitted ON attempts(student_id, submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_quiz_attempts_status ON quiz_attempts(status);
CREATE INDEX IF NOT EXISTS idx_quiz_questions_test_ordering ON quiz_questions(test_id, ordering);
CREATE INDEX IF NOT EXISTS idx_quiz_options_question_seq ON quiz_options(question_id, sequence_order);

CREATE INDEX IF NOT EXISTS idx_promo_code_claims_student_id ON promo_code_claims(student_id);
