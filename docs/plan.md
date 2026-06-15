# План реализации проекта (MVP CTF Platform)

## Этап 1: Подготовка инфраструктуры и БД
- [ ] 1.1. Обновление/создание миграций Flyway для MVP (базовые таблицы).
  - [ ] Таблица `users` (id, username, password_hash, role, created_at).
  - [ ] Таблица `challenges` (id, title, description, flag, points, is_active, created_at).
  - [ ] Таблица `submissions` (id, user_id, challenge_id, submitted_flag, is_correct, created_at).
- [ ] 1.2. Запуск Docker Compose для проверки работоспособности БД и применения миграций.

## Этап 2: Базовая аутентификация и Security
- [ ] 2.1. Добавление/обновление зависимостей `spring-boot-starter-security`, `jjwt`.
- [ ] 2.2. Конфигурация `SecurityFilterChain` (открытие `/api/auth/**`, закрытие остальных эндпоинтов).
- [ ] 2.3. Реализация генерации и валидации JWT токенов (`JwtService`, `JwtAuthenticationFilter`).
- [ ] 2.4. Создание контроллера и сервиса для регистрации и логина (`AuthController`, `AuthService`, `LoginDTO`, `RegisterDTO`).

## Этап 3: Управление пользователями (Профиль)
- [ ] 3.1. Создание сущности `UserEntity` и репозитория `UserRepository`.
- [ ] 3.2. Создание `UserService` для получения информации о текущем пользователе.
- [ ] 3.3. Создание `UserController` (эндпоинт `GET /api/users/me`).

## Этап 4: Управление задачами (Challenges)
- [ ] 4.1. Создание сущности `ChallengeEntity` и `ChallengeRepository`.
- [ ] 4.2. Создание `ChallengeService` с базовыми методами CRUD.
- [ ] 4.3. Создание `ChallengeController`:
  - [ ] Эндпоинты для ADMIN (создание, удаление, редактирование).
  - [ ] Эндпоинты для STUDENT (получение списка доступных задач без вывода реального флага).

## Этап 5: Проверка флагов (Submissions)
- [ ] 5.1. Создание сущности `SubmissionEntity` и `SubmissionRepository`.
- [ ] 5.2. Реализация бизнес-логики сабмита в `SubmissionService`:
  - [ ] Проверка, не была ли задача уже успешно решена этим пользователем.
  - [ ] Сравнение введенного флага с эталонным из БД.
  - [ ] Сохранение попытки со статусом `is_correct`.
- [ ] 5.3. Добавление эндпоинта `POST /api/challenges/{id}/submit`.

## Этап 6: Лидерборд
- [ ] 6.1. Написание SQL/JPQL запроса для подсчета баллов пользователей на основе их **первых** успешных сабмитов.
- [ ] 6.2. Создание `LeaderboardService` и `LeaderboardDTO`.
- [ ] 6.3. Эндпоинт `GET /api/leaderboard` в соответствующем контроллере.

## Этап 7: Обработка ошибок (Global Exception Handling)
- [ ] 7.1. Разработка кастомных исключений (`NotFoundException`, `AccessDeniedException`, `ValidationException`).
- [ ] 7.2. Реализация глобального `@RestControllerAdvice` для маппинга исключений в удобные JSON ответы (согласно RFC 7807 или принятому формату).

## Этап 8: Документация API и Тесты
- [ ] 8.1. Подключение и базовая настройка Swagger (`springdoc-openapi`).
- [ ] 8.2. Описание эндпоинтов соответствующими аннотациями Swagger.
- [ ] 8.3. Написание Unit/Integration тестов для критической бизнес-логики (Submissions, Leaderboard).
