# CTF & Academic Assessment Platform

> Веб-платформа для проведения соревнований, автоматизации учебного процесса и интерактивной оценки знаний студентов.

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)](https://www.postgresql.org/)
[![React](https://img.shields.io/badge/React-18-61DAFB?logo=react)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-blue?logo=typescript)](https://www.typescriptlang.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker)](https://www.docker.com/)
[![AI-Driven](https://img.shields.io/badge/Development-AI_Agent_Orchestrated-purple?logo=google-gemini)](https://deepmind.google/technologies/gemini/)

---

## 📌 О проекте

Платформа решает задачу совмещения практических CTF-соревнований с академическим контролем успеваемости студентов в вузе. 
Система позволяет не просто проверять флаги задач, но и связывать результаты с учебными потоками, академическими группами, баллами за лабораторные работы и динамическими шкалами итогового оценивания.

---

## 🚀 Ключевой функционал

### 🎯 CTF & Оценка знаний
* **Задания и флаги:** Категории задач, безопасная валидация сабмитов, режим «First Blood» и защита от брутфорса.
* **Практические задания с файлами:** Загрузка отчетов/решений для ручной проверки преподавателем с автоматическим коэффициентом первенства (100% / 50% / 10%).
* **Соревнования и тайминги:** Управление турнирами, динамический лидерборд с обновлением в реальном времени через **Server-Sent Events (SSE)**.
* **Тесты и квизы:** Встроенный движок теоретического тестирования с контролем времени и автозавершением попыток (`QuizScheduler`).
* **Геймификация:** Промокоды с различными типами эффектов и ручные корректировки баллов (`ScoreAdjustment`).

### 🎓 Академический модуль
* **Иерархия:** Потоки $\to$ Академические группы $\to$ Студенты $\to$ Привязка учетных записей (`Binding`).
* **Лабораторные работы:** Учет выполнения работ, расчет коэффициента успеваемости ($v_1$), массовый импорт оценок из CSV.
* **Шкалы оценивания:** Настраиваемый перевод итогового коэффициента ($S = \frac{v_1 + v_2}{2}$) в академические оценки (2–5).
* **Аналитика и аудит:** Детальные дашборды прогресса, экспорт метрик в CSV и сквозной журнал аудита действий.

### 🔒 Безопасность и архитектура
* **Аутентификация и RBAC:** Spring Security + Stateless JWT с ролевой моделью (`ADMIN`, `STUDENT`).
* **Миграции данных:** 14 версий Flyway-миграций со строгой реляционной целостностью схем PostgreSQL.
* **Frontend Architecture:** Архитектура клиентской части выстроена по методологии **Feature-Sliced Design (FSD)** на базе React + TypeScript + Vite.

---

## 🛠 Стек технологий

* **Backend:** Java 21, Spring Boot 3, Spring Data JPA, Spring Security, PostgreSQL 16, Flyway, JUnit 5, Mockito, Gradle.
* **Frontend:** React 18, TypeScript, Vite, Feature-Sliced Design (FSD), Zustand, React Router DOM 6, CSS Modules / Custom CSS.
* **DevOps & Tooling:** Docker, Docker Compose, Nginx, Gradle.
* **AI Orchestration:** Gemini Flash / Pro, Prompt Engineering.

---

## 📁 Архитектура проекта

```text
├── .agents/               # Правила, контекст и планы для AI-агентов
├── docs/                  # ТЗ (requirements.md), архитектура и ограничения
├── backend/               # Серверная часть (Spring Boot 3 + Gradle)
│   ├── src/main/java/com/arcx/ctfplatform/
│   │   ├── academic/      # Учебные потоки, группы, аналитика и шкалы оценок
│   │   ├── attempts/      # Проверка флагов, сабмиты файлов и история сдачи
│   │   ├── audit/         # Сквозной аудит событий платформы
│   │   ├── auth/          # Контроллер и сервисы аутентификации
│   │   ├── challenges/    # Каталог задач CTF и управление тасками
│   │   ├── competitions/  # Управление соревнованиями
│   │   ├── leaderboard/   # Скоринг, расчет метрик и SSE-стриминг
│   │   ├── modifiers/     # Промокоды и ручные корректировки баллов
│   │   ├── students/      # Учет лабораторных работ и импорт баллов
│   │   ├── tests/         # Квизы, вопросы и планировщик попыток
│   │   └── common/        # Spring Security (JWT), фильтры, глобальные исключения
│   └── src/main/resources/db/migration/ # 14 Flyway-миграций
├── frontend/              # Клиентская часть (React + Vite + FSD)
└── docker-compose.yml     # Стек для развертывания (Postgres, Backend, Frontend)
```

---

## ⚡️ Быстрый старт

### Требования
* **Docker** и **Docker Compose**
* **Git**

*(Для локальной сборки без Docker дополнительно понадобятся: Java 21, Node.js 20+).*

### Запуск через Docker Compose

1. Клонируйте репозиторий:
   ```bash
   git clone https://github.com/ArcxExe/project-CTF.git
   cd project-CTF
   ```

2. Соберите и запустите контейнеры:
   ```bash
   docker compose up --build
   ```

### Точки доступа

| Сервис | URL | Описание |
| :--- | :--- | :--- |
| **Frontend** | `http://localhost:3000/` | Веб-интерфейс платформы |
| **Backend API** | `http://localhost:8080/` | REST API |

---

## 👤 Демо-пользователи

После старта базы данных автоматически применяются сид-данные (`V2__seed_demo_data.sql`):

| Роль | Логин (username / email) | Пароль |
| :--- | :--- | :--- |
| **Администратор** | `admin` / `admin@ctf.local` | `password` |
| **Студент** | `student` / `student@ctf.local` | `password` |

