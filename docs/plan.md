# Детальный план реализации проекта (CTF Platform & Academic Grading)

Настоящий документ описывает пошаговый план разработки платформы с разделением на 12 этапов. Архитектура проекта строго разделяет слои (`Controller` -> `Service` -> `Repository`), использует конструкторы для внедрения зависимостей, а сущности (Entity) никогда не выходят за пределы сервисного слоя в REST API (используются строгие DTO контракты).

---

## Этап 1: Подготовка инфраструктуры, БД и миграций Flyway

### 1.1. Создание/обновление SQL-миграций Flyway
Разработка схемы БД с поддержкой ссылочной целостности и индексов:
1. `users` (id, email, username, password_hash, role, status, created_at, updated_at).
2. `academic_flows` (id, name, academic_year, created_at).
3. `academic_groups` (id, name, flow_id, created_at).
4. `students` (id, first_name, last_name, middle_name, group_id, student_code, user_id, status, created_at, updated_at).
   - Индекс на `student_code` (unique).
   - Индекс на `user_id` (unique, nullable).
5. `lab_scores` (id, student_id, score, created_at, updated_at).
6. `lab_score_history` (id, student_id, old_score, new_score, changed_by_user_id, reason, created_at).
7. `quizzes` (id, competition_id, title, duration_minutes, starts_at, ends_at, is_summed_to_ctf, shuffle_questions).
8. `quiz_questions` (id, quiz_id, text, type [SINGLE, MULTIPLE, SEQUENCE], points, correct_sequence_data, order_index).
9. `quiz_options` (id, question_id, text, is_correct, order_index).
10. `quiz_submissions` (id, student_id, quiz_id, score, started_at, submitted_at, answers_json).
11. `challenges` (id, competition_id, title, description, type [FLAG, CHOICE, SEQUENCE, FILE_UPLOAD], points, flag_or_answers, is_first_blood_only, created_at).
12. `attempts` (id, student_id, challenge_id, submitted_answer, file_path, is_correct, points_awarded, multiplier, submission_index, submitted_at, reviewer_id, review_comment, reviewed_at).
13. `promo_codes` (id, code, modifier_type [FIXED_ADD, FIXED_SUB, DOUBLE_COEFF, TEMP_MULTIPLIER], value, duration_seconds, is_used, used_by_student_id, used_at).
14. `score_adjustments` (id, student_id, points, reason, created_by_user_id, created_at).
15. `grading_scales` (id, min_coefficient, max_coefficient, grade, description).
16. `audit_logs` (id, timestamp, user_id, username, role, action_type, target_object, old_value, new_value, ip_address).

### 1.2. Docker Compose и Проверка
- Подготовка Docker Compose (PostgreSQL, PgAdmin, Redis для кэширования/сессий).
- Запуск и верификация успешного применения всех миграций Flyway.

---

## Этап 2: Базовая аутентификация, Security и Регистрация со связыванием

### 2.1. Конфигурация Spring Security и JWT
- Настройка `SecurityFilterChain` с разграничением ролей: `ADMIN` (преподаватель) и `STUDENT`.
- Реализация `JwtAuthenticationFilter` и `JwtService` с использованием секретного ключа из конфигурации.

### 2.2. Сервис и контроллер авторизации (Auth)
- `AuthController` и `AuthService` с использованием строгих DTO (`LoginRequest`, `RegisterRequest`, `AuthResponse`).
- При регистрации пользователя (`RegisterRequest`):
  - Поиск записи `Student` по введенному `studentCode`.
  - Если запись `Student` найдена, создается сущность `User`, статус привязки переходит в `PENDING_APPROVAL`.
  - Если запись не найдена, регистрация приостанавливается с ошибкой «Шифр студента не найден в предварительном списке».
- Эндпоинты подтверждения привязки для `ADMIN`:
  - `GET /api/admin/pending-bindings` — список неподтвержденных связей.
  - `POST /api/admin/pending-bindings/{id}/approve` — подтверждение связи.
  - `POST /api/admin/pending-bindings/{id}/reject` — отклонение привязки.

---

## Этап 3: Академическая структура и импорт студентов

### 3.1. CRUD для потоков, групп и студентов
- Создание JPA-сущностей `AcademicFlow`, `AcademicGroup`, `Student`.
- Реализация репозиториев, сервисов и контроллеров с авторизацией (доступ только для `ADMIN`).

### 3.2. Импорт списков студентов (CSV/Excel)
- Реализация парсера файлов в `StudentImportService`.
- Эндпоинт `POST /api/admin/students/import` (принимает MultipartFile).
- Процесс импорта: создание сущностей `Student` с привязкой к указанным группам (если группа не существует — автоматическое создание или ошибка импорта).

### 3.3. Статусы участников
- Реализация эндпоинта `PATCH /api/admin/students/{id}/status` для изменения статуса студента (`ACTIVE`, `BLOCKED`, `OUT_OF_RATING`, `DISQUALIFIED`).
- Добавление проверки статуса при любой авторизации (заблокированные пользователи моментально лишаются JWT-сессии).

---

## Этап 4: Модуль учета лабораторных работ и расчет $v_1$

### 4.1. Внесение и редактирование баллов
- Реализация эндпоинта `POST /api/admin/lab-scores` для ручного выставления/редактирования баллов студента.
- Каждая транзакция изменения баллов сохраняет запись в `lab_score_history` с указанием причины (DTO `LabScoreAdjustmentRequest` содержит `points`, `reason`).
- Реализация импорта баллов лабораторных из CSV/Excel: `POST /api/admin/lab-scores/import`.

### 4.2. Расчет $M_{max}$ и коэффициента лабораторных ($v_1$)
- `LabScoreService` вычисляет $M_{max}$ на основе заданных фильтров совокупности:
  - `GroupScope` (список UUID академических групп).
  - `FlowScope` (UUID потока).
  - `GlobalScope` (все студенты).
- Формула: $v_1 = M / M_{max}$. Если у студента $M > M_{max}$ (в случае ошибки ввода или превышения лимитов), коэффициент $v_1$ принудительно ограничивается единицей или рассчитывается строго по пропорции.

---

## Этап 5: Модуль тестового этапа (Квизы)

### 5.1. CRUD Конструктор Квизов для ADMIN
- Сущности `Quiz`, `QuizQuestion`, `QuizOption`.
- Эндпоинты создания квиза, добавления вопросов с типами `SINGLE`, `MULTIPLE`, `SEQUENCE`.
- Сохранение правильной последовательности ответов в `correct_sequence_data` (в виде сериализованного JSON массива индексов).

### 5.2. Прохождение квиза студентами (Student Quiz Flow)
- Эндпоинт `POST /api/quizzes/{id}/start` — фиксирует время начала прохождения в `quiz_submissions`.
- Эндпоинт `POST /api/quizzes/{id}/submit` — отправка ответов.
- **Автоматическая проверка:**
  - `SINGLE`: 100% баллов, если выбран правильный вариант.
  - `MULTIPLE`: Сравнение множеств. Полное совпадение — 100% баллов.
  - `SEQUENCE`: Проверка совпадения порядка элементов.
- Автоматическая фиксация по истечении лимита времени (таймер на сервере блокирует повторную отправку, принудительно закрывая сессию теста).

---

## Этап 6: Модуль задач CTF, First Blood и Задачи с файлом

### 6.1. Задачи с автопроверкой и загрузкой файлов
- Эндпоинты `ChallengeController` для отправки решений: `POST /api/challenges/{id}/submit`.
- Для типа `FILE_UPLOAD`: сохранение файла на сервере/хранилище, запись пути `file_path`, перевод статуса попытки в `PENDING_REVIEW`.

### 6.2. Механика First Blood (Первая кровь)
- В БД для задачи проверяется флаг `is_first_blood_only`.
- В `SubmissionService` при верной отправке:
  - Выполняется атомарная блокировка записи задачи на уровне СУБД (`SELECT ... FOR UPDATE`).
  - Проверяется, решена ли задача кем-то другим ранее.
  - Если да — попытка отклоняется с ошибкой.
  - Если нет — начисляются баллы, задача помечается решенной, генерируется SSE-событие «First Blood» для всех клиентов.

### 6.3. Ручная проверка решений (File Upload Review)
- Эндпоинты для `ADMIN` для проверки отправленных файлов: `GET /api/admin/manual-reviews` и `POST /api/admin/manual-reviews/{attemptId}/grade`.
- **Автоматический расчет процентов по времени отправки (индекс отправки):**
  - Метод `gradeAttempt` считает количество ранее отправленных верных решений этой задачи:
    - 0 решений -> коэффициент 1.0.
    - 1 решение -> коэффициент 0.5.
    - 2+ решений -> коэффициент 0.1.
  - Начисление баллов: `Points = MaxPoints * TimeMultiplier * ReviewerPercentMultiplier`.
  - ADMIN имеет право переопределить вычисленный балл, указав причину корректировки.

---

## Этап 7: Модуль игровых промокодов

### 7.1. Модель промокода
- Сущность `PromoCode`. Поля: `code` (уникальный ключ, unique index), `modifierType`, `value`, `isUsed`, `usedByStudentId`, `usedAt`.
- Эндпоинты для `ADMIN` (просмотр списка, создание, удаление промокода).

### 7.2. Логика активации промокода
- Эндпоинт `POST /api/promo-codes/claim` (для студентов).
- Вызов метода `PromoCodeService.claimCode` с аннотацией `@Transactional` и пессимистической блокировкой (`findByCode` с `LockModeType.PESSIMISTIC_WRITE`):
  - Проверка `isUsed`. Если `true` — бросается исключение.
  - Установка `isUsed = true`, `usedByStudentId = student.id`, `usedAt = Instant.now()`.
  - Применение эффекта (моментальное зачисление баллов при `FIXED_ADD` / списание при `FIXED_SUB`).
  - Временные эффекты (`DOUBLE_COEFF`, `TEMP_MULTIPLIER`) записываются в кэш Redis с TTL для учета в последующих сабмитах студента в течение соревнований.

---

## Этап 8: Административные корректировки баллов

### 8.1. Сущность ScoreAdjustment
- Ведение ручных корректировок. Таблица `score_adjustments`.
- Эндпоинт `POST /api/admin/adjustments` (DTO `ScoreAdjustmentRequest` содержит `studentId`, `points`, `reason`).
- При сохранении корректировки пересчитывается текущий рейтинг студента, данные отправляются в шину SSE.

---

## Этап 9: Итоговые показатели ($v_2, S$) и Шкала оценок

### 9.1. Итоговый расчет рейтинга CTF
- `LeaderboardService` вычисляет сумму баллов студента ($N$) с учетом:
  - Задач CTF (автоматических и проверенных ручных).
  - Тестов (если у соответствующего квиза стоит флаг `is_summed_to_ctf`).
  - Промокодов (`FIXED_ADD`, `FIXED_SUB`, коэффициенты).
  - Ручных корректировок `score_adjustments`.
- Вычисление $N_{max}$ на основе настроенной совокупности (группа, поток, все).
- Расчет показателя CTF: $v_2 = N / N_{max}$.
- Расчет итогового семестрового коэффициента: $S = (v_1 + v_2) / 2$.

### 9.2. Динамическая шкала оценок
- Сущность `GradingScale` (id, min_coefficient, max_coefficient, grade [2, 3, 4, 5], description).
- Эндпоинты CRUD для `GradingScale` (доступно только `ADMIN`).
- Сервис `GradingService` при получении рейтинга маппит коэффициент $S$ каждого студента в оценку на основе актуальных записей из таблицы `grading_scales`.

---

## Этап 10: Логирование аудита (Audit Log)

### 10.1. Реализация перехватчика событий аудита
- Создание аннотации `@AuditAction(actionType = ...)` для методов контроллеров/сервисов.
- Использование Spring AOP (AspectJ) для перехвата вызовов аннотированных методов.
- Сбор контекста:
  - Авторизованный пользователь (из `SecurityContextHolder`).
  - IP-адрес запроса (из `HttpServletRequest`).
  - Старое и новое значение (путем сериализации сущностей до и после выполнения бизнес-логики).
- Асинхронная запись логов в БД с помощью `@Async` для исключения задержек в работе бизнес-методов.

---

## Этап 11: Экспорт отчетов (CSV/Excel)

### 11.1. Сервис экспорта
- Реализация `ReportService` с использованием библиотек для генерации файлов (например, Apache POI для Excel или OpenCSV для CSV).
- Настройка стриминга файлов напрямую в `HttpServletResponse` для снижения нагрузки на ОЗУ сервера.

### 11.2. Регламентированные эндпоинты отчетов
Доступно только пользователям с ролью `ADMIN`:
- `GET /api/admin/reports/final-grading` — Итоговый отчет по студентам (ФИО, Группа, Поток, $M, v_1, N, v_2, S$, Рекомендуемая оценка).
- `GET /api/admin/reports/submissions` — Отчет по решениям задач.
- `GET /api/admin/reports/manual-reviews` — Отчет по ручным проверкам файлов.
- `GET /api/admin/reports/adjustments` — Лог административных корректировок.

---

## 12. Реактивный интерфейс, Интеграция, Тесты и Документация

### 12.1. Реактивное обновление лидерборда (SSE)
- Реализация `SseController` с эндпоинтом `GET /api/leaderboard/live` для получения обновлений рейтинга в реальном времени.
- `LeaderboardService` содержит пул активных `SseEmitter`.
- При успешном сабмите, ручной корректировке или активации промокода вызывается метод `broadcastLeaderboardUpdate()`, рассылающий JSON со снимком рейтинга.

### 12.2. Защита от сбоев при прохождении тестов
- Реализация автосохранения ответов квиза: периодический `POST /api/quizzes/{id}/auto-save` со стороны фронтенда (сохранение черновых ответов в БД).
- Дублирование сохранения текущих ответов в LocalStorage браузера студента.

### 12.3. Документация API и Тесты
- Подключение `springdoc-openapi-ui` для генерации спецификации OpenAPI 3.0.
- Написание интеграционных тестов с использованием Testcontainers (база PostgreSQL) для проверки:
  - Конкурентной активации промокодов.
  - Атомарной фиксации First Blood.
  - Корректности расчета итоговых коэффициентов $v_1, v_2, S$ и результирующей оценки.
