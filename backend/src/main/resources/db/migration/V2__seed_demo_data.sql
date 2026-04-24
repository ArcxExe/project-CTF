INSERT INTO users (id, email, password_hash, role, status)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'admin@ctf.local', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000002', 'student@ctf.local', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'STUDENT', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000003', 'instructor@ctf.local', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'INSTRUCTOR', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO academic_streams (id, name)
VALUES ('10000000-0000-0000-0000-000000000001', 'Computer Science 2026')
ON CONFLICT (id) DO NOTHING;

INSERT INTO academic_groups (id, name, stream_id)
VALUES ('20000000-0000-0000-0000-000000000001', 'CS-26-A', '10000000-0000-0000-0000-000000000001')
ON CONFLICT (id) DO NOTHING;

INSERT INTO students (id, user_id, group_id, student_code)
VALUES ('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '20000000-0000-0000-0000-000000000001', 'STU-0001')
ON CONFLICT (id) DO NOTHING;

INSERT INTO competitions (id, title, description, starts_at, ends_at, status)
VALUES (
    '40000000-0000-0000-0000-000000000001',
    'Intro CTF Demo',
    'Учебное соревнование для демонстрации платформы',
    NOW() + INTERVAL '1 day',
    NOW() + INTERVAL '2 days',
    'PUBLISHED'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO challenges (id, competition_id, title, description, points)
VALUES (
    '50000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    'Warmup Crypto',
    'Найти флаг в простом шифре',
    100
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO attempts (id, challenge_id, student_id, submitted_flag, is_correct)
VALUES (
    '60000000-0000-0000-0000-000000000001',
    '50000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000001',
    'CTF{demo_attempt}',
    FALSE
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO audit_events (id, actor_user_id, action, details)
VALUES (
    '70000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'SEED_DATA_LOADED',
    '{"source":"flyway","version":"V2"}'::jsonb
)
ON CONFLICT (id) DO NOTHING;
