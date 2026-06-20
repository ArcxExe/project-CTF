# Объединенный план: Обучение + Разработка CTF MVP

## Цель и фокус обучения
Получение минимально достаточного набора навыков для разработки backend-части CTF-платформы (Java 21, Spring Boot 3, PostgreSQL). 
Архитектура не должна содержать бизнес-логики в контроллерах и репозиториях. Зависимости внедряются через конструктор. Сущности БД (Entity) не возвращаются через REST API (используются DTO).

---

## Еженедельный план (Теория + Практика)

### День 1. Spring Boot и структура проекта
**Цель:** Понять IoC, DI и базовую конфигурацию приложения.
*Теория к просмотру:*
- [ ] 3. Inversion of Control. Dependency Injection
- [ ] 4. IoC Container
- [ ] 6. Constructor Injection
- [ ] 18. @Autowired / @Value
- [ ] 19. Classpath Scanning
- [ ] 24. Java-based Configuration
- [ ] 31. Spring Boot. Введение
- [ ] 33. Spring Boot. Настройка проекта
- [ ] 34. @SpringBootApplication
- [ ] 35. Lombok
- [ ] 36. Properties
- [ ] 37. Yaml format
- [ ] 38. @ConfigurationProperties
- [ ] 40. Logback Configuration

*Что нужно понять:*
*   Как Spring создает beans.
*   Почему Controller получает Service через constructor injection.
*   Как работает `application.yml`.

*Практика:*
- [ ] Сгенерировать проект.
- [ ] Настроить `docker-compose.yml` для PostgreSQL.
- [ ] Подключить `application.yml`.
- [ ] Написать миграцию Flyway `V1__init_schema.sql` (таблицы users, challenges, submissions).
- [ ] Убедиться, что приложение стартует и БД инициализируется.

### День 2. REST и Controller layer
**Цель:** Создание REST API, маппинг эндпоинтов и DTO.
*Теория к просмотру:*
- [ ] 74. Web Starter. Введение
- [ ] 75. Dispatcher Servlet
- [ ] 76. @Controller
- [ ] 77. @RequestMapping
- [ ] 78. Parameters, Headers, Cookies
- [ ] 82. CRUD. API Design
- [ ] 83. CRUD. Service Layer
- [ ] 94. REST. Введение
- [ ] 95. REST. Практика

*Что нужно понять:*
*   Как создать REST endpoint и принять request DTO.
*   Почему нельзя отдавать Entity напрямую наружу.

*Практика (Модуль Users/Groups):*
- [ ] Спроектировать эндпоинты (GET, POST, PUT, DELETE) для базового ресурса (например, User или StudentGroup).
- [ ] Создать request/response DTO.
- [ ] Написать Controller (пока без сложной бизнес-логики).

### День 3. JPA и Service layer
**Цель:** Доменная модель, миграции и бизнес-логика.
*Теория к просмотру:*
- [ ] 46. Data JPA Starter. Введение
- [ ] 47. Data JPA Starter. Подключение
- [ ] 48. Hibernate Entities
- [ ] 53. Repository
- [ ] 57. @Query
- [ ] 58. @Modifying
- [ ] 72. Liquibase. Теория (воспринимать как концепт для Flyway)
- [ ] 73. Liquibase. Практика

*Что нужно понять:*
*   Entity — это маппинг на БД, а не объект для передачи клиенту.
*   Repository — интерфейс для запросов, а не для бизнес-правил.

*Практика:*
- [ ] Создать `UserEntity` и `ChallengeEntity`.
- [ ] Создать интерфейсы `UserRepository` и `ChallengeRepository`.
- [ ] Написать `ChallengeService` (базовый CRUD).
- [ ] Подключить Controller к Service.

### День 4. Validation и ошибки
**Цель:** Глобальная обработка исключений и валидация данных.
*Теория к просмотру:*
- [ ] 91. Validation Starter. Введение
- [ ] 92. Custom validator
- [ ] 93. @ControllerAdvice / @ExceptionHandler
- [ ] 96. Swagger. API docs

*Что нужно понять:*
*   Как провалидировать входные DTO (`@NotNull`, `@NotBlank`).
*   Как перехватывать исключения централизованно, чтобы отдавать клиенту JSON (RFC 7807 или кастомный формат).
*   Как документировать API.

*Практика:*
- [ ] Добавить аннотации валидации в DTO.
- [ ] Создать `GlobalExceptionHandler` (`@RestControllerAdvice`).
- [ ] Добавить кастомные исключения `NotFoundException` и `BadRequestException`.
- [ ] Описать эндпоинты в Swagger.

### День 5. Транзакции и конкурентность
**Цель:** Атомарность операций и защита от состояния гонки (Race Condition).
*Теория к просмотру:*
- [ ] 49. @Transactional. TestContext
- [ ] 50. TransactionAutoConfiguration
- [ ] 51. @Transactional Settings
- [ ] 52. Manual Transactions
- [ ] 62. @Lock / @QueryHints

*Что нужно понять:*
*   Где правильно ставить `@Transactional` (на уровне Service).
*   Как предотвратить ситуацию, когда два студента одновременно отправляют флаг и оба получают статус "первый решивший".

*Практика (Модуль Submissions):*
- [ ] Создать `SubmissionEntity`.
- [ ] Написать `SubmissionService.submitFlag()`.
- [ ] Обеспечить защиту от повторной сдачи задания одним пользователем (атомарная проверка).

### День 6. Security basics и GET /api/me
**Цель:** Аутентификация, авторизация и JWT.
*Теория к просмотру:*
- [ ] 99. Security Starter. Введение
- [ ] 100. Authentication Architecture
- [ ] 101. DaoAuthenticationProvider
- [ ] 104. PasswordEncoder
- [ ] 106. Authorization Architecture
- [ ] 107. Method Security
- [ ] 108. Access to authenticated user
- [ ] 109. CSRF Filter
- [ ] 114. JWT. JSON Web Token
- [ ] 115. Swagger Authorization

*Что нужно понять:*
*   Authentication vs Authorization.
*   Как достать текущего пользователя из `SecurityContext`.
*   Почему CSRF отключается для JWT.

*Практика:*
- [ ] Настроить `SecurityConfig` (отключить CSRF, настроить CORS).
- [ ] Реализовать генерацию и проверку JWT (`JwtService`, `JwtAuthenticationFilter`).
- [ ] Добавить `AuthController` (`/login`, `/register`).
- [ ] Реализовать эндпоинт `GET /api/users/me` (получение профиля по токену).

### День 7. Тесты
**Цель:** Интеграционное и модульное тестирование.
*Теория к просмотру:*
- [ ] 41. Test Starter
- [ ] 42. Integration Testing. Part 1
- [ ] 43. Integration Testing. Part 2
- [ ] 44. Integration Testing. Part 3
- [ ] 71. Testcontainers
- [ ] 84. Spring MVC Testing
- [ ] 110. Security Testing

*Что нужно понять:*
*   Как поднять реальную БД для тестов (Testcontainers).
*   Как тестировать REST API через `MockMvc`.

*Практика:*
- [ ] Написать Unit-тесты для `ChallengeService` (через Mockito).
- [ ] Написать интеграционный тест на отправку флага (успешный сабмит и отказ при дубликате).
- [ ] Написать тест проверки доступа (студент не может удалить задачу).

---

## Правила работы с локальным ИИ (Antigravity)
1. **Запрет на генерацию бизнес-логики целиком.**
   - Хороший запрос: "Создай DTO и SQL-миграцию для сущности Submission. Controller и Service я напишу сам."
2. **Использование в качестве ревьюера.**
   - Пример запроса: "Проверь мой SubmissionService на уязвимость к состоянию гонки. Укажи на ошибки, но не переписывай код целиком."
3. **Контроль версий пакетов.**
   - Требовать использования `jakarta.*` (Spring Boot 3), а не устаревшего `javax.*`.
