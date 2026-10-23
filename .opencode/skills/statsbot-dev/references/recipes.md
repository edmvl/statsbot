# Рецепты типовых задач

## Новая страница Mini App

1. `MiniAppController` (`rest/`): `@GetMapping("/app/<path>")` → модель → имя шаблона.
   Права: `adminAuthGuard.allow(request, response, true)` для админских разделов,
   иначе — `currentUserService.idAsString(request)` + ручная проверка.
2. Если раздел только для админов чата — добавь путь в `WebMvcConfig` (интерсептор).
3. Шаблон `templates/<page>.html`: `<link th:href="@{/css/theme.css}">`,
   `app-back.js` с `data-back`, стили — на переменных темы, никаких хардкод-цветов.
4. Новые элементы в `index.html` — с атрибутом нужной роли
   (`data-super-admin-only`, `data-trip-admin-only`) и `hidden`.
5. Тест: рендер в `TemplateRenderTest` (мокай DTO, добавь атрибуты модели).
6. Визуальная проверка (см. `debugging.md` → «Визуальная проверка»).

## Новая команда бота

1. `BotCommands`: `NAME(".команда", "Пояснение")`.
2. `@Service implements CommandExecutable`, проверка `messageDTO.getUserText().startsWith(...)`,
   парсинг аргумента/реплая, ответ через `messageExecutor`.
3. Не забыть удалить сообщение команды (`deleteMessage`), если так делают соседние команды.
4. `HelpService` — добавить строку в справку `/start`.
5. Тест с моками `MessageExecutor` (см. `UserStatusServiceTest`, `LogServiceTest`).
6. Если нужен доступ по API Telegram — оборачивай в `MessageExecutor`, ошибки логируй
   и возвращай `null`/`false`, не роняй обработчик.

## Новая миграция

1. `V{n}__snake_case.sql` (следующий номер, ничего не переиспользовать).
2. Идемпотентно: `add column if not exists`, `create index if not exists`,
   `create table if not exists`; заполнение старых строк — `update ... where ... is null`
   до `set not null`.
3. Проверить на одноразовом Postgres (см. `data-migrations.md`), в том числе повторный
   прогон.
4. Обновить сущность/проекции/запросы и рендер-тесты — Hibernate схему не создаёт.

## Новое поле в существующей таблице

1. Миграция `alter table ... add column if not exists`.
2. Поле в `@Entity` (`@Column(name = "...")`).
3. Если поле нужно на странице — добавь в native-запрос и интерфейсную проекцию
   (`ChatMessageDto`, `TripViewDto`, …), затем в шаблон с проверкой на `null`.
4. Тесты: сервис (сохранение/обновление) + рендер с новым полем.

## Жалобы и баны

- Создание: `FeedbackService.processFeedback(complaint, userId, targetUserId, files)` —
  вложения в `{upload-dir}/{feedbackId}`.
- Правка пользователя, на кого жалоба: `updateTargetUser(...)` — автор/админ, нельзя
  после бана.
- Закрытие с причиной: `closeFeedback(...)` → сообщение автору.
- Переоткрытие: `reopenFeedback(...)` — только автор.
- Бан: `banForFeedback(...)` → API-бан, `banned_user`, сообщение в канал, ссылка на жалобу.
- Смержить/удалить жалобу нельзя (нет метода) — при необходимости писать новый.

## Реклама

- Предложение из `ad-proposal.html` → `AdsService` (`PENDING`) + вложения
  `{upload-dir}/ads/{adsId}`.
- Одобрение/отклонение — супер-админ на `/app/ads`.
- Оплата — `CreateInvoiceLink` (`XTR`, пустой `provider_token`, см. `StarsInvoiceLink`),
  подтверждение — `pre_payment`/`successful_payment`.
- Показ — `Job` по расписанию, только в чатах с `enable_ads`, сверка — `reconcileAds`.

## Проверка «глазами» после правки вёрстки

См. `debugging.md`, раздел «Визуальная проверка без запуска приложения»: временный
`PreviewDumpTest` → `python -m http.server` → скриншот в браузере → удалить артефакты.
Обязательно проверь:
- светлую тему (по умолчанию) — этого достаточно, цвета на переменных;
- что новые кнопки/блоки видны и не «прозрачны» (`[hidden]`);
- клики по новым элементам (через `browser.evaluate`).

## Перед коммитом/ответом

1. `mvn -o test` зелёный.
2. `git status` — только ожидаемые файлы, никаких `preview/`, `target/`, временных тестов.
3. Секреты не попали в код/тесты/логи.
4. В ответе: что сделано, чем проверено, какие ограничения (например, Bot API не отдаёт
   удаления сообщений).
