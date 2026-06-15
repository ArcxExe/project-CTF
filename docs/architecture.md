# CTF Platform - Проектирование взаимодействия (Architecture)

## Слои приложения
Приложение строится по классической 3-звенной архитектуре:
1. **Controllers (REST API):** Принимают HTTP запросы, валидируют входящие DTO, вызывают сервисы, формируют и отдают HTTP ответы (DTO).
2. **Services (Business Logic):** Содержат основную бизнес-логику и правила платформы, обеспечивают транзакционность (`@Transactional`).
3. **Repositories (Data Access):** Spring Data JPA интерфейсы для взаимодействия с PostgreSQL.

## Структура пакетов (Package Structure)
```text
com.arcx.ctfplatform
├── config/        # Глобальная конфигурация (Cors, Swagger)
├── security/      # Настройки Security, JWT, фильтры, сервисы аутентификации
├── common/        # Общие утилиты, кастомные исключения, ControllerAdvice, базовые классы
├── users/         # Модуль пользователей (Entity, Repository, Service, Controller, DTO)
├── challenges/    # Модуль задач (управление CTF тасками)
├── submissions/   # Модуль попыток (проверка флагов, история сабмитов)
└── leaderboard/   # Модуль рейтинга (агрегация баллов)
```

## Маппинг DTO
- Преобразование Entity <-> DTO должно происходить на уровне слоя Service или с помощью специальных классов-мапперов.
- Контроллер должен работать строго с DTO, никогда не принимая и не возвращая Entity.

## Контракты API (Базовые наброски)
- `POST /api/auth/register` - Регистрация пользователя.
- `POST /api/auth/login` - Авторизация, получение JWT.
- `GET /api/users/me` - Получение профиля текущего пользователя.
- `GET /api/challenges` - Получение списка доступных задач.
- `POST /api/challenges` - (ADMIN) Создание новой задачи.
- `POST /api/challenges/{id}/submit` - Отправка флага на проверку.
- `GET /api/leaderboard` - Получение текущего рейтинга.
