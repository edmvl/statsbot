---
name: Statsbot Development
description: Разработка и отладка проекта statsbot (Telegram-бот статистики + Mini App): архитектура, авторизация через Telegram initData, Thymeleaf-шаблоны и темы, Flyway-миграции, обработка апдейтов бота, подводные камни и проверенные рецепты. Использовать при любой задаче в этом репозитории.
---

# statsbot: база знаний проекта

Проект: **messageStatBot** — Telegram-бот статистики чатов и Mini App (Thymeleaf) для
попутчиков: отзывы, подписки, реклама за звёзды, жалобы и баны, админские страницы.

Стек: Java 16, Spring Boot 2.5.3, Spring Data JPA + Postgres, Flyway, Thymeleaf 3.1.2,
telegrambots 6.9.7.1 (long polling), немного jQuery в webjars.

## Как работать с этим скиллом

1. Сначала прочитай `references/architecture.md` — карта кода и потоков.
2. Для задач по Mini App (страницы, вёрстка, авторизация) — `references/miniapp.md`.
3. Для бота, команд, апдейтов, Telegram API — `references/telegram-bot.md`.
4. Для БД, миграций, сущностей — `references/data-migrations.md`.
5. Если что-то «не работает» или нужен визуальный/локальный прогон — `references/debugging.md`.
6. Пошаговые чек-листы типовых задач — `references/recipes.md`.
7. Почему сделано именно так (и что уже отклонено) — `references/decisions.md`.
   Перед спорным изменением сверься: возможно, это уже обсуждали.
8. Гео-квесты (карта, чекпоинты, прохождение) — `references/quests.md`.

Правила и команды сборки продублированы в `AGENTS.md` (он загружается всегда).

## Карта кода (кратко)

```
src/main/java/ru/zhendozzz/tgbot/messageStatBot/
  app/MyTelegramBot.java        точка входа апдейтов: processor.process(update)
  processor/Processor.java      гейт по типу апдейта (message / edited_message / inline / pre_checkout)
  handler/MessageHandler.java   Update -> MessageDTO, команды + NeedProcessMessage
  handler/InlineMessageHandler  inline-режим (список команд)
  message_executor/             отправка/удаление/баны/аватарки/инвойсы (тонкая обёртка над API)
  service/                      бизнес-логика (BotCommands-сервисы, AdsService, FeedbackService, LogService…)
  rest/                         Mini App: MiniAppController и админские контроллеры
  config/                       AdminAuthGuard, интерсепторы, профили, требование конфигурации
  entity/ repo/ dto/            JPA-модель, репозитории, проекции
  util/                         KeyboardUtils, AccessConstants, TelegramRequest, enums/
  Job.java                      @Scheduled: статистика, розыгрыши, реклама, напоминания
src/main/resources/
  templates/*.html              страницы Mini App
  static/css/theme.css          общие переменные тем Telegram
  static/js/app-back.js         «назад», спиннер, cookie авторизации, раскрытие админ-меню
  db/migration/                 Flyway (V1 … V17)
src/test/java/…                 unit + TemplateRenderTest (рендер всех шаблонов)
```

## Два главных потока

**Сообщение в чате:** `MyTelegramBot.onUpdateReceived` → `Processor.process` →
`MessageHandler.choose` (строит `MessageDTO`, отсекает бота и служебное) →
`CommandExecutable` (если текст начинается с команды) и `NeedProcessMessage`
(лог, пользователи, участники, фильтр слов, уведомление забаненному, платежи).

**Страница Mini App:** браузер/Telegram → `app-back.js` кладёт `initData` в cookie
`tg_webapp_auth` → `CurrentUserService`/`AdminAuthGuard` проверяют подпись →
интерсепторы (`WebMvcConfig`) пускают в админские разделы → контроллер в `rest/`
отдаёт Thymeleaf-шаблон.

## Незыблемые правила

- Пользователь — только из подписанного `initData`; id из формы/квери не доверять.
- Права — на сервере, в каждом методе. Скрытие кнопки в шаблоне ничего не защищает.
- Все страницы — адаптивные к светлой/тёмной теме Telegram (переменные `theme.css`).
- Схема БД — только Flyway-миграцией; SQL идемпотентный.
- `mvn -o test` должен быть зелёным; тестов сейчас ~100.
- Файлы не переписывать через PowerShell (кодировки) — только `edit`/`write`.

## Чего в проекте нет (не пытайся «добавить»)

- **API списка забаненных участников** — Telegram его не отдаёт; баны ведутся в БД
  (`banned_user`) и синхронизируются вручную/баном из Mini App.
- **Событий удаления сообщений** у ботов нет: `deleted_business_messages` работает
  только для бизнес-аккаунтов. Пометка «удалено» ставится лишь там, где бот удалил сам
  (фильтр слов).
- **`web_app`-кнопок в группах/каналах** — Telegram отклоняет; в чаты шлём текст или
  `url`-кнопку, `web_app` — только в личку.
- Локального пользователя-бота (MTProto) — только Bot API.

## Подводные камни, которые уже стоили времени

- Thymeleaf/SpEL: `!null` и `#lists.size(null)` кидают исключение — проверяй на `null`
  (`attr != null and !#lists.isEmpty(attr)`).
- `[hidden] { display: none !important }` в `theme.css`: показывать/скрывать только
  атрибутом `hidden`, не через `style.display`.
- Модель шаблона должна быть null-safe: `TemplateRenderTest` рендерит страницы без
  части атрибутов и ловит падения.
- Кодировка: консоль Windows показывает кириллицу как `?????` — это не порча файла;
  проверяй `[System.IO.File]::ReadAllText(...).Contains(...)`.
- Правки сообщений приходят как `edited_message` и легко теряются в гейте `Processor`.
- Старые строки `log` без `message_id` ищутся по `(chat_id, user_id, date_time)`.

## Быстрые команды

```powershell
$env:JAVA_HOME="C:\Users\info\.jdks\ms-17.0.17"
$mvn="C:\Program Files\JetBrains\IntelliJIdea2026.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
& $mvn -o test
& $mvn -o test "-Dtest=TemplateRenderTest"
```

Локальная БД: `docker compose up -d` (поднимает Postgres и заливает `db_dump.bak`,
он в `.gitignore`). Если дампа нет — база будет пустой, страницы отдадут пустые списки.
