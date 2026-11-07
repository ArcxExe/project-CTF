-- Attendance module: classes presence tracking for 8 academic cycles (8 lectures + 8 practices).
-- Attendance does not affect the rating score (regulation 3.4), it is a standalone accounting module.

ALTER TABLE students ADD COLUMN IF NOT EXISTS pair_number INTEGER;

CREATE TABLE IF NOT EXISTS student_attendance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    session_type VARCHAR(20) NOT NULL,
    cycle_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    comment VARCHAR(255),
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    CONSTRAINT chk_student_attendance_session_type CHECK (session_type IN ('LECTURE', 'PRACTICE')),
    CONSTRAINT chk_student_attendance_cycle_number CHECK (cycle_number BETWEEN 1 AND 8),
    CONSTRAINT chk_student_attendance_status CHECK (status IN ('PRESENT', 'ABSENT', 'EXCUSED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_student_attendance_cell
    ON student_attendance (student_id, session_type, cycle_number);
