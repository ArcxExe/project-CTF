# CTF Platform - Проектирование взаимодействия (Architecture)

## Архитектурный стиль
Платформа спроектирована как **модульный монолит** (Backend на Spring Boot 3) и клиентское SPA-приложение (Frontend на React + TypeScript + Vite).

---

## Слои приложения (Backend)
Приложение строится по классической 3-звенной архитектуре:
1. **Controllers (REST API):** Принимают HTTP-запросы, валидируют входящие DTO (`@Valid`), вызывают сервисы, формируют и отдают стандартизированные ответы (DTO / `ResponseEntity`).
2. **Services (Business Logic):** Содержат бизнес-правила платформы, расчет метрик ($v_1, v_2, S$), проверку флагов, обеспечивают транзакционность (`@Transactional`).
3. **Repositories (Data Access):** Spring Data JPA интерфейсы для взаимодействия с СУБД PostgreSQL.

---

## Структура пакетов Backend (`com.arcx.ctfplatform`)
```text
com.arcx.ctfplatform
├── academic/       # Академическая структура: потоки (streams), группы, шкалы оценок, аналитика и импорт студентов
├── attempts/       # Попытки сдачи решений флагов и файлов (Attempt), история попыток
├── audit/          # Журнал сквозного аудита критических действий пользователей (AuditLog)
├── auth/           # Аутентификация, генерация/проверка JWT, Spring Security конфигурация
├── challenges/     # Модуль CTF-задач (CtfTask) и управление тасками
├── common/         # Общие утилиты, кастомные исключения, GlobalExceptionHandler (@RestControllerAdvice)
├── competitions/   # Соревнования (Competition), привязка задач и временные рамки
├── leaderboard/    # Рейтинговая таблица, расчет метрик, стриминг обновлений через SSE
├── modifiers/      # Игровые промокоды (PromoCode) и ручные корректировки баллов (ScoreAdjustment)
├── scoring/        # Сервисы пересчета и агрегации баллов
├── students/       # Учет успеваемости: баллы за лабораторные работы (LabScore), импорт оценок
├── tests/          # Модуль тестирования: квизы, вопросы, варианты ответов и попытки прохождения
└── users/          # Сущность User, роли (STUDENT, ADMIN, INSTRUCTOR), профиль пользователя
```

---

## Архитектура Frontend (`frontend/src`)
Frontend построен с использованием модульной FSD-подобной (Feature-Sliced Design) структуры:
- **`app/`**: Инициализация роутинга (`router.tsx`), глобальные стили и переменные темы.
- **`pages/`**: Страницы приложения для участников и администраторов (Dashboard, Competitions, Students, AdminAnalytics, AdminGradingScale, TestRunner и др.).
- **`widgets/`**: Композиционные блоки разметки (например, `AppLayout`).
- **`features/`**: Интерактивные фичи с бизнес-логикой (`auth`, переключение `theme`).
- **`entities/`**: Бизнес-сущности и состояния (уведомления и пр.).
- **`shared/`**: Переиспользуемые UI-компоненты (Card, Modal, DataTable, Badge, Button, Toaster), API-клиент (Axios/Fetch), типы TypeScript.

---

## Правила работы с данными (DTO & Entity)
- **Изоляция Entity:** Контроллеры строго работают с DTO (Request/Response) и никогда не принимают и не возвращают JPA Entity наружу.
- **Маппинг:** Преобразование Entity <-> DTO осуществляется в сервисном слое или через вспомогательные методы/мапперы.
- **Реактивность:** Обновления лидерборда передаются на клиент в режиме реального времени через SSE (Server-Sent Events) по каналу `/api/leaderboard/stream`.

---

## Основные группы REST API Контрактов

### 1. Аутентификация и пользователи (`/api/auth`, `/api/users`)
- `POST /api/auth/register` — Регистрация нового пользователя (студента).
- `POST /api/auth/login` — Вход в систему (по username или email), получение JWT токена.
- `GET /api/users/me` — Профиль текущего авторизованного пользователя.

### 2. Академическая структура и студенты (`/api/streams`, `/api/groups`, `/api/admin/students`)
- `GET /api/streams`, `POST /api/streams`, `DELETE /api/streams/{id}` — Управление академическими потоками.
- `GET /api/groups`, `POST /api/groups`, `DELETE /api/groups/{id}` — Управление академическими группами.
- `GET /api/admin/students` — Список студентов с их статусами и привязкой к аккаунтам.
- `POST /api/admin/students/import` — Массовый импорт студентов из CSV.
- `PUT /api/admin/students/{id}/status` — Изменение статуса студента (ACTIVE, BLOCKED, OUT_OF_RATING, DISQUALIFIED).
- `POST /api/admin/students/{id}/bind-user` — Привязка учетной записи к студенту.

### 3. Соревнования и задачи (`/api/competitions`, `/api/challenges`)
- `GET /api/competitions`, `GET /api/competitions/{id}` — Список соревнований и информация о конкретном соревновании.
- `POST /api/admin/competitions`, `PUT /api/admin/competitions/{id}`, `DELETE /api/admin/competitions/{id}` — Администрирование соревнований.
- `GET /api/challenges`, `POST /api/challenges`, `PUT /api/challenges/{id}` — Управление задачами CTF.
- `GET /api/challenges/{id}/flag` — (ADMIN) Просмотр флага задачи.

### 4. Отправка решений и попытки (`/api/attempts`)
- `POST /api/attempts/{id}/submit` — Отправка текстового флага на проверку.
- `POST /api/attempts/{id}/submit-file` — Загрузка файла решения для задач с ручной проверкой.
- `GET /api/attempts/history` — История попыток сдачи текущего пользователя.
- `GET /api/admin/attempts/pending` — Список решений, ожидающих ручной проверки преподавателем.
- `POST /api/admin/attempts/{id}/grade` — Выставление оценки и коэффициента за проверенное решение.

### 5. Квизы и тестирование (`/api/quizzes`, `/api/tests`, `/api/admin/quizzes`)
- `POST /api/quizzes/{quizId}/start` — Старт прохождения теста (запуск серверного таймера).
- `GET /api/quizzes/{quizId}/questions` — Получение вопросов теста.
- `POST /api/quizzes/{quizId}/submit` — Отправка ответов на тест.
- `GET /api/quizzes/attempts` — Попытки прохождения тестов.
- `POST /api/admin/quizzes/tests/{testId}/questions` — Добавление вопросов и вариантов ответов администратором.

### 6. Лабораторные работы (`/api/lab-scores`, `/api/admin/lab-scores`)
- `GET /api/lab-scores` — Просмотр баллов за лабораторные работы.
- `POST /api/admin/lab-scores` — Добавление/изменение баллов за лабораторные работы с указанием причины.
- `POST /api/admin/lab-scores/import` — Массовый импорт баллов из CSV.

### 7. Модификаторы баллов: Промокоды и Корректировки (`/api/promo-codes`, `/api/score-adjustments`)
- `POST /api/promo-codes/activate` — Активация промокода студентом.
- `GET /api/admin/promo-codes`, `POST /api/admin/promo-codes` — Управление промокодами.
- `GET /api/score-adjustments` — История корректировок.
- `POST /api/admin/score-adjustments` — Начисление бонуса или штрафа преподавателем с указанием основания.

### 8. Лидерборд, аналитика и шкалы оценок (`/api/leaderboard`, `/api/admin/analytics`, `/api/admin/grading-scales`)
- `GET /api/leaderboard` — Текущий снимок лидерборда (баллы CTF, лабы, коэффициенты $v_1, v_2, S$).
- `GET /api/leaderboard/stream` — Подключение к Server-Sent Events (SSE) для обновления таблицы лидеров в реальном времени.
- `GET /api/metrics/export-v2` — Экспорт метрик студентов в формате CSV.
- `GET /api/admin/analytics/group`, `/tasks`, `/student-tests`, `/student-challenges` — Сводная аналитика успеваемости и решения задач.
- `GET /api/admin/grading-scales`, `POST /api/admin/grading-scales` — Настройка динамической шкалы перевода коэффициента $S$ в оценку (2, 3, 4, 5).

