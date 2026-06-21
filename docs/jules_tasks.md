# Jules AI Agent - Пошаговые задания на реализацию (Task Backlog)

Этот документ содержит скорректированные, структурированные и самодостаточные задания (промпты) для ИИ-агента Jules. Задачи выровнены с реальным состоянием репозитория (включая использование версии Flyway миграции `V8`) и базой данных.

---

### 🏁 ШАГ 1: Создание миграции БД

**Промпт для Jules:**
```text
Task: Implement Flyway Schema Migration for Extended Academic & CTF Features (Stage 1)

Context:
Our project uses Flyway for handling PostgreSQL database migrations. The current migration files in `backend/src/main/resources/db/migration/` go up to V7. Your task is to extend the database schema according to the final system specification. 

Instructions:
1. Create a new migration file named exactly `V8__academic_and_grading.sql` in the directory: `backend/src/main/resources/db/migration/`.
2. Write clean PostgreSQL-compatible DDL statements to create/modify the following tables:
   - academic_flows (id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, academic_year VARCHAR(20) NOT NULL, created_at TIMESTAMP NOT NULL)
   - academic_groups (id UUID PRIMARY KEY, name VARCHAR(50) NOT NULL, flow_id UUID REFERENCES academic_flows(id), created_at TIMESTAMP NOT NULL)
   - students (id UUID PRIMARY KEY, first_name VARCHAR(100) NOT NULL, last_name VARCHAR(100) NOT NULL, middle_name VARCHAR(100), group_id UUID REFERENCES academic_groups(id), student_code VARCHAR(50) UNIQUE NOT NULL, user_id UUID UNIQUE REFERENCES users(id), status VARCHAR(30) NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)
   - lab_scores (id UUID PRIMARY KEY, student_id UUID REFERENCES students(id) UNIQUE, score INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)
   - lab_score_history (id UUID PRIMARY KEY, student_id UUID REFERENCES students(id), old_score INTEGER NOT NULL, new_score INTEGER NOT NULL, changed_by_user_id UUID, reason TEXT NOT NULL, created_at TIMESTAMP NOT NULL)
   - manual_submissions (id UUID PRIMARY KEY, student_id UUID REFERENCES students(id), challenge_id UUID REFERENCES challenges(id), file_path VARCHAR(255), points_earned INTEGER, checked_by UUID, checked_at TIMESTAMP, percentage_multiplier INTEGER, status VARCHAR(30))
   - promo_codes (id UUID PRIMARY KEY, code VARCHAR(50) UNIQUE NOT NULL, modifier_type VARCHAR(30) NOT NULL, value NUMERIC(10,2), is_used BOOLEAN NOT NULL DEFAULT FALSE, used_by_student_id UUID REFERENCES students(id), activated_at TIMESTAMP)
   - grading_scales (id UUID PRIMARY KEY, min_coefficient NUMERIC(3,2) NOT NULL, max_coefficient NUMERIC(3,2) NOT NULL, grade INTEGER NOT NULL, description VARCHAR(150))
   - audit_logs (id UUID PRIMARY KEY, timestamp TIMESTAMP NOT NULL, user_id UUID, username VARCHAR(100), role VARCHAR(30), action_type VARCHAR(50) NOT NULL, target_object VARCHAR(100), old_value TEXT, new_value TEXT, ip_address VARCHAR(45))

3. Ensure proper foreign key constraints, types, and required unique indices. Do NOT write any Java entities on this step, generate only the SQL migration file.
```

---

### 📦 ШАГ 2: Сущности и импорт студентов

**Промпт для Jules:**
```text
Task: Implement Academic Entities and CSV Student Import Service (Stage 2)

Requirements:
1. Create JPA Entities: AcademicFlow, AcademicGroup, Student in the `com.arcx.ctfplatform.academic` domain package. Set up proper `@ManyToOne` relationships (Student -> AcademicGroup -> AcademicFlow).
2. Create Spring Data JPA repositories for each new entity.
3. Implement `StudentImportService`:
   - Add a method `parseAndImportStudents(InputStream csvStream)` that processes CSV rows (Format: LastName, FirstName, MiddleName, StudentCode, GroupName, FlowName).
   - If the AcademicFlow or AcademicGroup does not exist, create it automatically.
   - Save new Student rows into the database.
4. Create `AdminStudentController` protected with `@PreAuthorize("hasRole('ADMIN')")`:
   - POST /api/admin/students/import (via MultipartFile)
   - GET /api/admin/students (supports pagination and filtering)
   - PATCH /api/admin/students/{id}/status (changes enum status: ACTIVE, BLOCKED, OUT_OF_RATING, DISQUALIFIED).
5. Enforce constraints: Inject dependencies via constructors only. Use pure Request/Response DTO contracts for controller input/output, never leak raw JPA entities to the client.
```

---

### 🔐 ШАГ 3: Регистрация и привязка аккаунтов

**Промпт для Jules:**
```text
Task: Refactor Registration Flow and Add Student Account Binding (Stage 3)

Requirements:
1. Update `AuthService.register()` logic:
   - Registration request must include a `studentCode`.
   - Verify if a `Student` with this code exists in the DB. If not, throw 400 Bad Request ("Student code not registered").
   - If the student is already linked to another `user_id`, throw 400 Bad Request ("This student code is already linked").
   - If valid, create the `User` entity, but change the `Student` account status to a pending binding verification state.
2. In `AdminStudentController`, implement endpoints for managing account links:
   - GET /api/admin/students/pending-bindings (list of students awaiting confirmation)
   - POST /api/admin/students/{id}/approve-binding (locks user_id to Student completely)
   - POST /api/admin/students/{id}/reject-binding (clears user_id and disables/deletes the created User).
3. Update Security: ensure that if a student is BLOCKED or DISQUALIFIED, any request with their active JWT gets rejected by the security filter chain.
```

---

### 📊 ШАГ 4: Баллы за лабораторные и показатель v1

**Промпт для Jules:**
```text
Task: Implement Lab Scores Management and v1 Coefficient Calculation (Stage 4)

Requirements:
1. Map JPA entities for `LabScore` and `LabScoreHistory` following the V8 DDL schema.
2. Create `LabScoreService`:
   - Single point modification: updates `LabScore` and saves audit historical rows inside `LabScoreHistory` in a single transaction.
   - Import lab points from a CSV file (StudentCode, Points).
   - Dynamic M_max Calculation: write a query or method that finds the highest lab points among a configured cohort (entire group, entire flow, or globally).
   - Coefficient v1 Formula: Calculate v1 = M / M_max. Ensure it scales linearly between [0.0; 1.0].
3. Create `AdminLabScoreController` (restricted to ADMIN):
   - POST /api/admin/lab-scores (set single score)
   - POST /api/admin/lab-scores/import (mass update from CSV file)
   - GET /api/admin/lab-scores (returns leaderboard sheets for labs)
```

---

### 📝 ШАГ 5: Тестовый этап (Квизы)

**Промпт для Jules:**
```text
Task: Implement Test Stage (Quizzes) with Automatic Grading (Stage 5)

Requirements:
1. Map JPA entities for `Quiz`, `QuizQuestion`, `QuizOption`, and `QuizSubmission` based on the V8 schema.
2. Support 3 types of questions in `QuizQuestion` (Enum: SINGLE, MULTIPLE, SEQUENCE). 
   - For SEQUENCE questions, store the correct ordering indices as a serialized string/JSON list in `correct_sequence_data`.
3. Implement `QuizService`:
   - Admin CRUD for managing quizzes and their options/questions.
   - Student start/submit quiz flows: track passing session duration.
   - Automatic grading: score is calculated and locked when session time limit or quiz availability window ends. Support strict correct sequence validation.
4. Create controllers:
   - `AdminQuizController` (restricted to ADMIN) for managing tests.
   - `QuizController` (restricted to STUDENT) for getting available quizzes, starting a test, and submitting answers.
```

---

### 🏆 ШАГ 6: Задачи CTF, First Blood и Задачи с файлом

**Промпт для Jules:**
```text
Task: Implement First Blood Locks and Practical Challenges with File Uploads (Stage 6)

Requirements:
1. Map JPA entity for `ManualSubmission` (from `manual_submissions` table) to manage user practical task reports.
2. Extend `SubmissionService` for text/choice/sequence challenges:
   - For challenges where `isFirstBloodOnly` is true, write a query using a pessimistic write lock (SELECT FOR UPDATE) on the challenge record to atomically capture the first correct solver.
   - If already solved by another student, reject and prevent points allocation. Emit SSE event "First Blood" for the first successful solver.
3. For practical tasks (`type = FILE_UPLOAD` in Challenge):
   - Implement file upload saving and record the attempt inside `manual_submissions` with status `PENDING`.
   - Calculate speed-based automatic grading multipliers: 1st correct file submission receives 100% points, 2nd receives 50%, 3rd and subsequent receive 10%.
   - Implement `AdminManualSubmissionController` (for ADMIN) with endpoints: `GET /api/admin/manual-reviews` and `POST /api/admin/manual-reviews/{id}/grade` to override the score and save comments.
```

---

### 🎟️ ШАГ 7: Игровые промокоды с блокировкой

**Промпт для Jules:**
```text
Task: Implement Single-Use Promo Codes with Concurrent Lock Protection (Stage 7)

Requirements:
1. Map JPA entity for `PromoCode` following the `promo_codes` DDL schema.
2. Implement transaction-safe claim logic in `PromoCodeService.claimCode()`:
   - Use `@Transactional` and lock the row on select (`findByCode` using `LockModeType.PESSIMISTIC_WRITE`) to prevent concurrent activation of the same code by multiple requests.
   - Set `isUsed = true`, link to student, set `activatedAt = Instant.now()`.
   - Apply effect immediately (FIXED_ADD to score / FIXED_SUB).
   - If modifier applies for a duration, store temporal coefficient in a Redis-based cache or session tracking.
3. Create `AdminPromoCodeController` supporting GET (list all), POST (create new), and DELETE (delete by id) endpoints.
```

---

### 🎯 ШАГ 8: Шкала оценок и расчет показателей v2 и S

**Промпт для Jules:**
```text
Task: Implement Grading Scale and Final Semester S-Coefficient Calculations (Stage 8)

Requirements:
1. Map JPA entity for `GradingScale` following the `grading_scales` DDL schema.
2. Implement `LeaderboardService` calculations:
   - Calculate student score N: Sum of CTF challenges + quizzes (if integrated) + adjustments + promo codes.
   - Calculate N_max dynamically as the maximum points actually scored among the configured scope (academic group, academic flow, or globally).
   - Calculate v2 = N / N_max.
   - Calculate S = (v1 + v2) / 2.
3. Implement `GradingService`:
   - Match coefficient S with configured database intervals in `grading_scales` to return the recommended grade (2, 3, 4, or 5).
4. Create controllers:
   - `AdminGradingScaleController` (restricted to ADMIN) for managing intervals.
   - `LeaderboardController` returning student ratings containing scores, solved count, v1, v2, S, and recommended grades.
```

---

### 🛡️ ШАГ 9: Сквозное логирование аудита (AOP)

**Промпт для Jules:**
```text
Task: Implement Aspect-Oriented Audit Logging for Critical Actions (Stage 9)

Requirements:
1. Map JPA entity for `AuditLog` following the `audit_logs` DDL schema.
2. Define annotation `@AuditAction(actionType = ...)`.
3. Create aspect `AuditAspect` using `@Aspect` to intercept executions of methods annotated with `@AuditAction`:
   - Retrieve current authenticated user and role from `SecurityContextHolder`.
   - Retrieve client IP address from `HttpServletRequest`.
   - Record target action type, old state (before method execution), and new state (after completion).
   - Save log entry asynchronously (using `@Async`) in the `audit_logs` database table.
4. Annotate actions: Auth (Login/Logout), submissions, promo codes, student status modifications, score adjustments, and grading scale updates.
```

---

### 📤 ШАГ 10: Экспорт отчетов (CSV/Excel)

**Промпт для Jules:**
```text
Task: Implement CSV/Excel Report Exporter for Admin Panel (Stage 10)

Requirements:
1. Implement `ReportService` using a low-overhead CSV writer (e.g. streaming directly to HttpServletResponse output stream).
2. Support generating 4 reports:
   - Final student grading spreadsheet (Name, group, flow, M, v1, N, v2, S, recommended grade).
   - Solutions log report.
   - Manual practical reviews report.
   - Admin score adjustments audit report.
3. Create `AdminReportController` (role ADMIN) mapping requests to file downloads (`Content-Disposition: attachment; filename=...`).
```
