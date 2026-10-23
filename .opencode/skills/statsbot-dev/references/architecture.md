# Архитектура

## Что это

Telegram-бот (`@hamoryalstatsbot` на проде) + Mini App для чата попутчиков:
статистика сообщений, отзывы о попутчиках, подписки на авторов, реклама с оплатой
звёздами, жалобы/баны, админские страницы.

Прод: `api.zhendozzz.ru`, порт `8081`, systemd-сервис `tgbot`, профиль `prod`.
Деплой: push в `main` → GitHub Actions (`.github/workflows/deploy.yml`) собирает
`mvn clean package`, гоняет `mvn test`, копирует jar в `/home/messageStatBot-0.0.1-SNAPSHOT.jar`
и делает `systemctl restart tgbot`.

## Технологии и версии

| Что | Версия/детали |
|---|---|
| Java | 16 в `pom.xml`, CI собирает JDK 17 |
| Spring Boot | 2.5.3 (javax, не jakarta) |
| Thymeleaf | 3.1.2 (spring5-диалект) |
| telegrambots | 6.9.7.1 (long polling) |
| БД | PostgreSQL (локально 12 в docker-compose) |
| Миграции | Flyway, `src/main/resources/db/migration` |
| Фронт | серверный Thymeleaf + jQuery 3.6.1 из webjars |

## Пакеты

| Пакет | Ответственность |
|---|---|
| `app` | `MyTelegramBot` — `TelegramLongPollingBot`, передаёт апдейт в `Processor` |
| `processor` | `Processor`/`DefaultProcessor`: маршрутизация по типу апдейта |
| `handler` | `MessageHandler` (Update → `MessageDTO`), `InlineMessageHandler` |
| `message_executor` | `MessageExecutor`/`Impl`: send/delete/ban/avatar/invoice/status |
| `service` | Вся бизнес-логика: команды, реклама, жалобы, лог, участники, платежи, задания |
| `rest` | Mini App: `MiniAppController`, `FeedbackController`, `ChatAdminsController`, `ChatSettingsController`, `MessageController` |
| `config` | `AdminAuthGuard`, интерсепторы, `WebMvcConfig`, профили, проверка конфигурации |
| `entity`, `repo`, `dto` | JPA-сущности, Spring Data, проекции для страниц |
| `util` | `KeyboardUtils`, `TelegramRequest`, `AccessConstants`, `enums/` |

## Поток сообщения из чата

```
MyTelegramBot.onUpdateReceived(update)
  └─ DefaultProcessor.process(update)
       ├─ hasPreCheckoutQuery  → AdPaymentService.confirmPreCheckout
       ├─ hasMessage || hasEditedMessage → MessageHandler.choose
       └─ hasInlineQuery       → InlineMessageHandler
            └─ MessageHandler.choose
                 ├─ отсекает сообщения самого бота (isOwnBot)
                 ├─ строит MessageDTO (+ edited/editDateTime, serviceMessage, file)
                 ├─ если текст начинается с команды → CommandExecutable#executeCommand
                 └─ всем NeedProcessMessage#processMessage:
                      LogService            — пишет сообщение (или правку) в log/log_edit
                      UserService           — обновляет users (имя, ник, аватар)
                      ChatMemberService     — участники чатов и «неактивные»
                      WordsFilterService    — удаляет мат/ссылки, помечает deleted
                      BanNotificationService— сообщает забаненному, что он забанен
                      AdPaymentService      — successful_payment по рекламе
```

Важно: `Processor` — единственный гейт по типу апдейта. Если добавляешь новый тип
(например, `edited_channel_post`), правь именно его, иначе обработчик не вызовется.

## Поток страницы Mini App

```
GET /app/... (Telegram WebView или браузер)
  ├─ app-back.js: initData → cookie tg_webapp_auth, один reload (_reloaded=1)
  ├─ интерсепторы WebMvcConfig (для админских разделов)
  ├─ CurrentUserService.idAsString(request)  → id из подписанного initData
  ├─ AdminAuthGuard.allow(...)               → bootstrap-страница или 403
  └─ контроллер → модель → Thymeleaf-шаблон
```

`initData` принимается из cookie `tg_webapp_auth`, заголовка `X-Telegram-Init-Data`
или параметра `_auth` (`TelegramRequest`). Подпись проверяется HMAC-SHA256 по токену
бота (`TelegramWebAppAuthService`), возраст — `telegram.webapp.auth.max-age-seconds`
(0 = не проверять).

## Планировщик (`Job.java`)

| Cron | Что делает |
|---|---|
| `0 0 0 * * ?` | ночная статистика по чатам, загрузка гороскопов |
| `0 0 10 * * ?` | «избранное» и напоминания |
| `0 * 8-23 * * ?` | завершение викторин/розыгрышей |
| `0 0 8 * * ?` | праздники |
| `0 0 1 * * ?` | напоминания |
| `0 37/38 18 * * ?` | обновление участников, письма неактивным |
| `0 0 8-21/2 ? * MON-THU`, `0 0 5-22 * * FRI-SUN` | показ рекламы |
| `0 0/10 * * * ?` | сверка оплаченной рекламы (`reconcileAds`) |

## Файлы на диске

База — `${file.upload-dir}` (прод `/home/edmvl/bot_files/`):

```
{upload-dir}/ads/{adsId}/...        вложения объявлений
{upload-dir}/{feedbackId}/...       вложения жалобы
{upload-dir}/fc-{commentId}/...     вложения комментария жалобы
{upload-dir}/avatars/{userId}.jpg   аватарки (скачивает UserService)
```

Раздача: `GET /images?id=..&name=..` и `GET /avatar/{userId}` (`MessageController`).
Каталог жалобы — **числовой id** (не зависит от публичных ссылок).

## Конфигурация

`src/main/resources/application.properties` (запасные значения) + профильные файлы:

| Профиль | Файл | Как включается |
|---|---|---|
| `local` | `config/application-local.properties` | по умолчанию |
| `prod` | `config/application-prod.properties` | `SPRING_PROFILES_ACTIVE=prod` |

`config/` в `.gitignore`; в репозитории только `application-*.example.properties`.
Env-переменные приоритетнее: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`,
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

`RequiredConfigEnvironmentPostProcessor` валит старт, если пуст токен или имя бота —
это защита от запуска «не тем ботом».

Flyway: `baseline-on-migrate=true`, `baseline-version=0`, `out-of-order=true`,
`placeholder-replacement=false` (в миграциях есть `${...}` в комментариях).

## Ключевые константы

| Что | Значение / где |
|---|---|
| Супер-админ | `115965184` (`AccessConstants.SUPER_ADMIN_ID`) |
| Канал поездок | из `chat_settings` (`setting_name='trip_channel'`, `setting_value='true'`) |
| Бот | `telegram.bot.username` (по умолчанию `hamoryalstatsbot`) |
| Ссылки на жалобы | `FeedbackService.FEEDBACK_URL` = `https://api.zhendozzz.ru/app/feedback-chat/` + id |
| Цена рекламы | `AdStatus.STARS_PER_DAY` (100 ⭐/день), `MAX_DAYS` |
