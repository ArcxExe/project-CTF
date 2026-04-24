# CTF Platform Backend (Spring Boot)

Базовый backend-проект учебной CTF-платформы в стиле модульного монолита.

## Технологии

- Java 21
- Spring Boot 3.x
- Gradle
- PostgreSQL
- Spring Web, Security, Data JPA, Validation, Actuator
- Flyway
- JWT authentication (skeleton)
- Docker Compose

## Архитектурные принципы

- Модульный монолит (без микросервисов)
- Разделение responsibility между controller/service/repository
- Business-логика в service layer
- Entity не возвращаются напрямую из REST API
- Внешние ответы через DTO
- Миграции через Flyway
- Секреты и конфигурация через env

## Быстрый старт (Docker)

```bash
cp .env.example .env
docker compose up --build
```

Backend: `http://localhost:8080`
PostgreSQL: `localhost:5432`

## Локальный запуск без Docker

```bash
cp .env.example .env
export $(grep -v '^#' .env | xargs)
./gradlew bootRun
```

## Базовые API endpoints

- `POST /api/auth/register` — регистрация
- `POST /api/auth/login` — логин

### Примеры curl

Логин теперь принимает поле `login` (email или username):

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "login": "student1@test.local",
    "password": "password123"
  }'
```

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "login": "student1",
    "password": "password123"
  }'
```

## Security rules

- `/api/auth/**` — без авторизации
- `/api/admin/**` — только `ADMIN`
- Остальные endpoints — только для авторизованных

## Flyway

- `V1__init_schema.sql` — базовая схема
- `V2__seed_demo_data.sql` — demo-данные
- `V3__add_username_to_users.sql` — username для входа по логину

## Demo users (из seed)

- `admin@ctf.local` (username `admin`) / `password`
- `student@ctf.local` (username `student`) / `password`
- `instructor@ctf.local` (username `instructor`) / `password`

## Что дальше

- Реализовать refresh tokens + revoke/blacklist
- Добавить полноценные модули students/competitions/challenges/leaderboard
- Ввести RBAC-проверки на уровне use-case
- Покрыть интеграционными тестами security и auth
