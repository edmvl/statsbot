# Mini App: авторизация, страницы, права, вёрстка

## Авторизация

1. Telegram открывает страницу с `#tgWebAppData=...` / `window.Telegram.WebApp.initData`.
2. `static/js/app-back.js` кладёт `initData` в **cookie `tg_webapp_auth`** (`path=/`,
   `samesite=lax`, `secure` на https) и один раз перезагружает страницу с `_reloaded=1`.
3. Сервер (`TelegramRequest.initData`) читает `initData` из cookie, заголовка
   `X-Telegram-Init-Data` или параметра `_auth`.
4. `TelegramWebAppAuthService.getVerifiedUserId(initData)` проверяет HMAC-SHA256
   (ключ — токен бота) и возвращает id или `null`.
5. `CurrentUserService.idAsString(request)` — id текущего пользователя (или `null`).
6. `AdminAuthGuard`:
   - `allow(request, response, allowTripChannelAdmins)` — для страниц;
   - `isTripAdmin(request)` — булева проверка без записи в ответ;
   - если `initData` нет → отдаётся `BOOTSTRAP_HTML` (страница, которая берёт
     `initData` из Telegram и перезагружает запрос) — **не голый 403**;
   - если подпись битая → тоже bootstrap (перевыпуск cookie).

**Правило:** `userId` никогда не берём из формы/квери. Действия проверяют права заново
на сервере, даже если кнопка в шаблоне скрыта.

## Права

| Что | Кто |
|---|---|
| Супер-админ | Telegram id `115965184` (`AccessConstants.SUPER_ADMIN_ID`) |
| Админ канала поездок | `settingsService.isUserAdmin(tripChannelId, userId)` |
| Админ чата | `settingsService.isUserAdmin(chatId, userId)`; канал — из `chat_settings` |

Интерсепторы (`WebMvcConfig`):

| Путь | Проверка |
|---|---|
| `/app/chats`, `/app/chat/**`, `/app/chat-settings/**`, `/app/chat-admins/**`, `/app/challenges/**` | супер-админ или админ этого чата |
| `/app/subscriptions`, `/app/ads`, `/api/ads/**` | только супер-админ |

Остальные страницы проверяют права внутри метода (например, `feedback-list`, `bans`,
`feedback-chat/{id}/close` — админ канала поездок).

## Страницы

| URL | Шаблон | Доступ |
|---|---|---|
| `/app` | `index.html` | всем (меню, админ-блок раскрывается JS) |
| `/app/chats` | `chats.html` | админ чата / супер |
| `/app/chat/{chatId}` | `chat.html` | админ чата / супер |
| `/app/chat-settings/{chatId}` | `chat-settings.html` | админ чата / супер |
| `/app/chat-admins/{chatId}` | `chat-admins.html` | админ чата / супер |
| `/app/trips` | `trips.html` («Отзывы о поездках») | всем |
| `/app/reviews/{userId}` | `driver-reviews.html` | всем |
| `/app/subscriptions`, `/app/my-subscriptions` | `subscriptions.html`, `my-subscriptions.html` | супер / свои подписки |
| `/app/ads`, `/app/ad-proposal` | `ads.html`, `ad-proposal.html` | супер / предложение рекламы |
| `/app/feedback` | `feedback.html` | форма обращения |
| `/app/feedback-chat/{id}` | `feedback-chat.html` | **просмотр всем** (в т.ч. без Telegram), действия — автор/админ |
| `/app/feedback-list`, `/app/bans` | `feedback-list.html`, `bans.html` | админ канала поездок / супер |
| `/app/quests` | `quests.html` | всем (управление — супер-админ) |
| `/app/quests/{id}` | `quest.html` | участник (любой авторизованный) |
| `/app/quests/{id}/edit`, `/app/quests/{id}/results` | `quest-edit.html`, `quest-results.html` | только супер-админ |
| `/app/error` (`error.html`) | ошибки 403/404/500 | — |

API: `/app/access` (JSON прав для меню), `/api/feedback/users?q=` (поиск пользователя),
`/api/ads/**`, `/images`, `/avatar/{id}`, `/send` (только супер-админ).

## Шаблоны и темы

- Общие переменные — `static/css/theme.css`: `--page-bg`, `--card-bg`, `--inner-bg`,
  `--text`, `--muted`, `--accent`, `--on-accent`, `--link`, `--danger`, `--line`,
  `--line-soft`, `--soft`, `--soft-strong`, `--good`, `--mid`, `--low`, `--star`,
  `--radius-*`, `--shadow*`, `--transition`.
- Никаких хардкод-цветов, кроме нейтральных `rgba(127,127,127,…)` и акцентов статусов.
- Тема Telegram задаёт `--tg-theme-*`; `app-back.js` выставляет `color-scheme`,
  чтобы нативные поля не выбивались.
- **`[hidden] { display: none !important }`** уже есть в `theme.css`. Показывать/скрывать
  элементы — только атрибутом `hidden` (`el.hidden = true`), не `style.display`,
  иначе `display:flex` на классе перебьёт скрытие.
- В `index.html` пункты меню скрыты и раскрываются: `[data-super-admin-id]`,
  `[data-super-admin-only]`, `[data-trip-admin-only]`, `[data-admin-section]`
  (через `/app/access`).

## app-back.js (что уже умеет)

- кнопка «← Назад» с адресом из `data-back` (и нативная `BackButton` Telegram);
- спиннер на внутренних переходах;
- установка/обновление cookie `initData` + один reload;
- раскрытие админ-пунктов по `initData` (id супер-админа) и `/app/access`;
- `color-scheme` для темы.

Скрипт подключается тегом:
`<script th:src="@{/js/app-back.js(v=12)}" data-back="/app" defer></script>`
(`v=12` — версия для сброса кеша; меняй при правках JS).

## Конвенции страниц

- Формы: обычный `POST` + `sendRedirect` обратно на страницу (PRG), без fetch, кроме
  AJAX-поиска и переключателей подписки.
- Скрытые поля — из модели (`th:value="${...}"`), не из JS-угадывания.
- Пагинация — параметры `page`/`count` (по умолчанию 30).
- Тексты пользователя — только `th:text` (экранирование), не `th:utext`.
- Модель должна быть **null-safe**: `TemplateRenderTest` рендерит страницы с частичной
  моделью — любое `attr.method()` без проверки на null уронит тест.
- Списки: `th:if="${attr != null and !#lists.isEmpty(attr)}"` — `#lists.size(null)`
  кидает исключение.

## Публичный просмотр жалоб (осознанное решение)

`/app/feedback-chat/{id}` отдаётся **и без `initData`** (ссылка из чата открывается
в браузере): страница рендерится в режиме чтения, `authenticated=false`, кнопок нет.
Действия (комментарий, закрытие, переоткрытие, бан, смена пользователя) требуют
подписи и проверяются в сервисе/контроллере. Номера жалоб — последовательные,
защиты от перебора нет (обсуждалось с владельцем, признано некритичным).
