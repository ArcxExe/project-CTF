# CTF Frontend Foundation

Базовый frontend-каркас для учебной CTF-платформы.

## Что уже есть

- React + TypeScript + Vite
- React Router
- Zustand для состояния
- mock API-слой
- типы данных
- базовая тема
- UI-kit: кнопки, поля, карточки, таблица, модалка, toast-уведомления
- layout под роли admin / participant
- стартовые страницы: dashboard, студенты, соревнования, профиль участника

## Запуск

```bash
npm install
npm run dev
```

## Архитектура

```text
src/
  app/            # инициализация приложения, роутинг, глобальные стили
  entities/       # базовые сущности и их локальное состояние
  features/       # бизнес-фичи (auth, theme)
  pages/          # страницы
  shared/         # ui, api, types, utils
  widgets/        # layout и составные блоки
```

## Что подключать следующим шагом

1. Реальный backend вместо `shared/api/mock`.
2. TanStack Query или другой data fetching layer при росте проекта.
3. Формальные схемы валидации.
4. WebSocket/SSE для рейтинга и статусов заданий.
5. Полноценные страницы теста и CTF-модуля.
