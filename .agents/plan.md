# Объединенный план: Обучение + Разработка CTF MVP

## Цель и фокус обучения
Получение минимально достаточного набора навыков для разработки backend-части CTF-платформы (Java 21, Spring Boot 3, PostgreSQL). 
Архитектура не должна содержать бизнес-логики в контроллерах и репозиториях. Зависимости внедряются через конструктор. Сущности БД (Entity) не возвращаются через REST API (используются DTO).

---

## Еженедельный план (Теория + Практика)

### День 1. Spring Boot и структура проекта
**Цель:** Понять IoC, DI и базовую конфигурацию приложения.
*Теория к просмотру:*
- [x] 3. Inversion of Control. Dependency Injection
- [x] 4. IoC Container
- [x] 6. Constructor Injection
- [x] 18. @Autowired / @Value
- [x] 19. Classpath Scanning
- [x] 24. Java-based Configuration
- [x] 31. Spring Boot. Введение
- [x] 33. Spring Boot. Настройка проекта
- [x] 34. @SpringBootApplication
- [x] 35. Lombok
- [x] 36. Properties
- [x] 37. Yaml format
- [x] 38. @ConfigurationProperties
- [x] 40. Logback Configuration

*Что нужно понять:*
*   Как Spring создает beans.
*   Почему Controller получает Service через constructor injection.
*   Как работает `application.yml`.

*Практика:*
- [x] Сгенерировать проект.
- [x] Настроить `docker-compose.yml` для PostgreSQL.
- [x] Подключить `application.yml`.
- [x] Написать миграцию Flyway `V1__init_schema.sql` (таблицы users, challenges, submissions).
- [x] Убедиться, что приложение стартует и БД инициализируется.

### День 2. REST и Controller layer
**Цель:** Создание REST API, маппинг эндпоинтов и DTO.
*Теория к просмотру:*
- [x] 74. Web Starter. Введение
- [x] 75. Dispatcher Servlet
- [x] 76. @Controller
- [x] 77. @RequestMapping
- [x] 78. Parameters, Headers, Cookies
- [x] 82. CRUD. API Design
- [x] 83. CRUD. Service Layer
- [x] 94. REST. Введение
- [x] 95. REST. Практика

*Что нужно понять:*
*   Как создать REST endpoint и принять request DTO.
*   Почему нельзя отдавать Entity напрямую наружу.

*Практика (Модуль Users/Groups):*
- [x] Спроектировать эндпоинты (GET, POST, PUT, DELETE) для базового ресурса (например, User или StudentGroup).
- [x] Создать request/response DTO.
- [x] Написать Controller (пока без сложной бизнес-логики).

### День 3. JPA и Service layer
**Цель:** Доменная модель, миграции и бизнес-логика.
*Теория к просмотру:*
- [x] 46. Data JPA Starter. Введение
- [x] 47. Data JPA Starter. Подключение
- [x] 48. Hibernate Entities
- [x] 53. Repository
- [x] 57. @Query
- [x] 58. @Modifying
- [x] 72. Liquibase. Теория (воспринимать как концепт для Flyway)
- [x] 73. Liquibase. Практика

*Что нужно понять:*
*   Entity — это маппинг на БД, а не объект для передачи клиенту.
*   Repository — интерфейс для запросов, а не для бизнес-правил.

*Практика:*
- [x] Создать `UserEntity` и `ChallengeEntity`.
- [x] Создать интерфейсы `UserRepository` и `ChallengeRepository`.
- [x] Написать `ChallengeService` (базовый CRUD).
- [x] Подключить Controller к Service.

### День 4. Validation и ошибки
**Цель:** Глобальная обработка исключений и валидация данных.
*Теория к просмотру:*
- [x] 91. Validation Starter. Введение
- [x] 92. Custom validator
- [x] 93. @ControllerAdvice / @ExceptionHandler
- [x] 96. Swagger. API docs

*Что нужно понять:*
*   Как провалидировать входные DTO (`@NotNull`, `@NotBlank`).
*   Как перехватывать исключения централизованно, чтобы отдавать клиенту JSON (RFC 7807 или кастомный формат).
*   Как документировать API.

*Практика:*
- [x] Добавить аннотации валидации в DTO.
- [x] Создать `GlobalExceptionHandler` (`@RestControllerAdvice`).
- [x] Добавить кастомные исключения `NotFoundException` и `BadRequestException`.
- [x] Описать эндпоинты в Swagger.

### День 5. Транзакции и конкурентность
**Цель:** Атомарность операций и защита от состояния гонки (Race Condition).
*Теория к просмотру:*
- [x] 49. @Transactional. TestContext
- [x] 50. TransactionAutoConfiguration
- [x] 51. @Transactional Settings
- [x] 52. Manual Transactions
- [x] 62. @Lock / @QueryHints

*Что нужно понять:*
*   Где правильно ставить `@Transactional` (на уровне Service).
*   Как предотвратить ситуацию, когда два студента одновременно отправляют флаг и оба получают статус "первый решивший".

*Практика (Модуль Submissions):*
- [x] Создать `SubmissionEntity`.
- [x] Написать `SubmissionService.submitFlag()`.
- [x] Обеспечить защиту от повторной сдачи задания одним пользователем (атомарная проверка).

### День 6. Security basics и GET /api/me
**Цель:** Аутентификация, авторизация и JWT.
*Теория к просмотру:*
- [x] 99. Security Starter. Введение
- [x] 100. Authentication Architecture
- [x] 101. DaoAuthenticationProvider
- [x] 104. PasswordEncoder
- [x] 106. Authorization Architecture
- [x] 107. Method Security
- [x] 108. Access to authenticated user
- [x] 109. CSRF Filter
- [x] 114. JWT. JSON Web Token
- [x] 115. Swagger Authorization

*Что нужно понять:*
*   Authentication vs Authorization.
*   Как достать текущего пользователя из `SecurityContext`.
*   Почему CSRF отключается для JWT.

*Практика:*
- [x] Настроить `SecurityConfig` (отключить CSRF, настроить CORS).
- [x] Реализовать генерацию и проверку JWT (`JwtService`, `JwtAuthenticationFilter`).
- [x] Добавить `AuthController` (`/login`, `/register`).
- [x] Реализовать эндпоинт `GET /api/users/me` (получение профиля по токену).

### День 7. Тесты
**Цель:** Интеграционное и модульное тестирование.
*Теория к просмотру:*
- [x] 41. Test Starter
- [x] 42. Integration Testing. Part 1
- [x] 43. Integration Testing. Part 2
- [x] 44. Integration Testing. Part 3
- [x] 71. Testcontainers
- [x] 84. Spring MVC Testing
- [x] 110. Security Testing

*Что нужно понять:*
*   Как поднять реальную БД для тестов (Testcontainers).
*   Как тестить REST API через `MockMvc`.

*Практика:*
- [x] Написать Unit-тесты для `ChallengeService` (через Mockito).
- [x] Написать интеграционный тест на отправку флага (успешный сабмит и отказ при дубликате).
- [x] Написать тест проверки доступа (студент не может удалить задачу).

---

## Правила работы с локальным ИИ (Antigravity)
1. **Запрет на генерацию бизнес-логики целиком.**
   - Хороший запрос: "Создай DTO и SQL-миграцию для сущности Submission. Controller и Service я напишу сам."
2. **Использование в качестве ревьюера.**
   - Пример запроса: "Проверь мой SubmissionService на уязвимость к состоянию гонки. Укажи на ошибки, но не переписывай код целиком."
3. **Контроль версий пакетов.**
   - Требовать использования `jakarta.*` (Spring Boot 3), а не устаревшего `javax.*`.
