-- 1. Rename challenges table to ctf_tasks
ALTER TABLE challenges RENAME TO ctf_tasks;

-- 2. Rename columns in ctf_tasks to match new entity fields
ALTER TABLE ctf_tasks RENAME COLUMN points TO base_score;
ALTER TABLE ctf_tasks RENAME COLUMN difficulty_level TO difficulty;
ALTER TABLE ctf_tasks ADD COLUMN category VARCHAR(100);

-- 3. Rename columns in competitions to match new entity fields
ALTER TABLE competitions RENAME COLUMN starts_at TO start_date;
ALTER TABLE competitions RENAME COLUMN ends_at TO end_date;

-- 4. Update status column constraints for competitions
-- Current check constraint check in postgres might not exist or be named. Let's drop constraint check if exists, then alter column.
-- Since competitions status was CHECK in init (wait, check was only on users in V1__init_schema.sql. Competitions status has no check constraint in V1.
-- Let's clean up any obsolete values or just leave it open since we're using Enum.
-- Wait, let's update existing statuses: DRAFT->DRAFT, PUBLISHED->ACTIVE, ARCHIVED->COMPLETED
UPDATE competitions SET status = 'ACTIVE' WHERE status = 'PUBLISHED';
UPDATE competitions SET status = 'COMPLETED' WHERE status = 'ARCHIVED';

-- 5. Update foreign key references in attempts
ALTER TABLE attempts DROP CONSTRAINT IF EXISTS attempts_challenge_id_fkey;
ALTER TABLE attempts RENAME COLUMN challenge_id TO task_id;
ALTER TABLE attempts ADD CONSTRAINT attempts_task_id_fkey FOREIGN KEY (task_id) REFERENCES ctf_tasks(id) ON DELETE CASCADE;

-- 6. Update foreign key references and rename test_challenges table to test_tasks
ALTER TABLE test_challenges DROP CONSTRAINT IF EXISTS test_challenges_challenge_id_fkey;
ALTER TABLE test_challenges RENAME COLUMN challenge_id TO task_id;
ALTER TABLE test_challenges RENAME TO test_tasks;
ALTER TABLE test_tasks ADD CONSTRAINT test_tasks_task_id_fkey FOREIGN KEY (task_id) REFERENCES ctf_tasks(id) ON DELETE CASCADE;

-- 7. Create junction table competition_tasks for the @ManyToMany relationship
CREATE TABLE competition_tasks (
    competition_id UUID NOT NULL REFERENCES competitions(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES ctf_tasks(id) ON DELETE CASCADE,
    PRIMARY KEY (competition_id, task_id)
);

-- 8. Migrate existing associations from ctf_tasks (old challenges) to competition_tasks
INSERT INTO competition_tasks (competition_id, task_id)
SELECT competition_id, id FROM ctf_tasks WHERE competition_id IS NOT NULL;

-- 9. Drop the old many-to-one competition_id column from ctf_tasks
ALTER TABLE ctf_tasks DROP COLUMN competition_id;
