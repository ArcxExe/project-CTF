DO $$
DECLARE
    v_student_user_id UUID;
    v_flow_id UUID;
    v_group_id UUID;
    v_student_id UUID;
BEGIN
    -- 1. Seed initial accounts ONLY when missing (never reset passwords or override statuses of existing accounts)
    IF NOT EXISTS (
        SELECT 1 FROM users
        WHERE id = '00000000-0000-0000-0000-000000000001'
           OR email = 'admin@ctf.local'
           OR username = 'admin'
    ) THEN
        INSERT INTO users (id, email, username, password_hash, role, status)
        VALUES (
            '00000000-0000-0000-0000-000000000001',
            'admin@ctf.local',
            'admin',
            '$2a$10$rbloaQUVm2fsPL7xcXzoJua0N9ugpi/hGInL51tU7pqsOftbhdXaC',
            'ADMIN',
            'ACTIVE'
        );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM users
        WHERE id = '00000000-0000-0000-0000-000000000003'
           OR email = 'instructor@ctf.local'
           OR username = 'instructor'
    ) THEN
        INSERT INTO users (id, email, username, password_hash, role, status)
        VALUES (
            '00000000-0000-0000-0000-000000000003',
            'instructor@ctf.local',
            'instructor',
            '$2a$10$rbloaQUVm2fsPL7xcXzoJua0N9ugpi/hGInL51tU7pqsOftbhdXaC',
            'INSTRUCTOR',
            'ACTIVE'
        );
    END IF;

    -- 2. Look up demo student account without resetting password_hash or status if it already exists
    SELECT id INTO v_student_user_id
    FROM users
    WHERE id = '00000000-0000-0000-0000-000000000002'
       OR email = 'student@ctf.local'
       OR username = 'student'
    ORDER BY id
    LIMIT 1;

    IF v_student_user_id IS NULL THEN
        v_student_user_id := '00000000-0000-0000-0000-000000000002';
        INSERT INTO users (id, email, username, password_hash, role, status)
        VALUES (
            v_student_user_id,
            'student@ctf.local',
            'student',
            '$2a$10$rbloaQUVm2fsPL7xcXzoJua0N9ugpi/hGInL51tU7pqsOftbhdXaC',
            'STUDENT',
            'ACTIVE'
        );
    END IF;

    -- 3. Ensure demo flow exists
    SELECT id INTO v_flow_id
    FROM academic_flows
    WHERE id = '10000000-0000-0000-0000-000000000001'
       OR name = 'Computer Science 2026'
    ORDER BY id
    LIMIT 1;

    IF v_flow_id IS NULL THEN
        v_flow_id := '10000000-0000-0000-0000-000000000001';
        INSERT INTO academic_flows (id, name, academic_year, created_at)
        VALUES (v_flow_id, 'Computer Science 2026', '2025/2026', NOW());
    END IF;

    -- 4. Ensure demo group exists
    SELECT id INTO v_group_id
    FROM academic_groups
    WHERE id = '20000000-0000-0000-0000-000000000001'
       OR name = 'CS-26-A'
    ORDER BY id
    LIMIT 1;

    IF v_group_id IS NULL THEN
        v_group_id := '20000000-0000-0000-0000-000000000001';
        INSERT INTO academic_groups (id, name, flow_id)
        VALUES (v_group_id, 'CS-26-A', v_flow_id);
    END IF;

    -- 5. Link demo student profile only if not already linked to another real user
    SELECT id INTO v_student_id
    FROM students
    WHERE user_id = v_student_user_id
    ORDER BY id
    LIMIT 1;

    IF v_student_id IS NULL THEN
        SELECT id INTO v_student_id
        FROM students
        WHERE (id = '30000000-0000-0000-0000-000000000001' OR student_code = 'STU-0001')
          AND user_id IS NULL
        ORDER BY id
        LIMIT 1;

        IF v_student_id IS NOT NULL THEN
            UPDATE students
            SET user_id = v_student_user_id,
                group_id = COALESCE(group_id, v_group_id)
            WHERE id = v_student_id
              AND user_id IS NULL;
        ELSIF NOT EXISTS (
            SELECT 1 FROM students
            WHERE id = '30000000-0000-0000-0000-000000000001'
               OR student_code = 'STU-0001'
        ) THEN
            INSERT INTO students (id, user_id, group_id, student_code, first_name, last_name, middle_name, status)
            VALUES (
                '30000000-0000-0000-0000-000000000001',
                v_student_user_id,
                v_group_id,
                'STU-0001',
                'Иван',
                'Иванов',
                'Иванович',
                'ACTIVE'
            );
        END IF;
    END IF;
END $$;
