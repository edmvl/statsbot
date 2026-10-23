# Telegram-бот: апдейты, команды, ограничения API

## Пайплайн апдейтов

`MyTelegramBot` (long polling) → `DefaultProcessor.process`:

```java
if (update.hasPreCheckoutQuery()) executePreCheckoutQuery(update);
if (update.hasMessage() || update.hasEditedMessage()) executeMessage(update);
if (update.hasInlineQuery()) executeInlineMessage(update);
```

`MessageHandler.choose`:

- берёт `update.getMessage()`, если пусто — `update.getEditedMessage()` (флаг `edited`);
- отбрасывает сообщения самого бота (`isOwnBot`, по `telegram.bot.username`);
- определяет служебные события (`isServiceMessage`) и файл (`getFile` по типам
  PHOTO/DOCUMENT/STICKER/VIDEO_NOTE/VIDEO/VOICE/ANIMATION/AUDIO);
- собирает `MessageDTO` (`chatId`, `chatName`, `messageId`, `replyMessageId`, `dateTime`,
  `userId/имя/ник`, `userText`, `file`, `serviceMessage`, `edited`, `editDateTime`);
- если текст начинается с команды **и сообщение не правка** — вызывает все
  `CommandExecutable`;
- затем — все `NeedProcessMessage` (лог, юзеры, участники, фильтр, уведомление, оплата).

Текст служебных событий («Вступил(а) в чат», «Изменил(а) название чата: …» и т.п.)
формируется в `MessageHandler.resolveText`.

## Команды (`util/enums/BotCommands`)

| Команда | Кто обрабатывает |
|---|---|
| `/start` | `HelpService` (справка; новые команды добавлять сюда же) |
| `.топ`, `.неделя`, `.дни` | `StatsService` |
| `.цензура` | `WordsFilterService` |
| `/reg` | `ChallengeService` |
| `.поездки` | `TripService` (web_app-кнопка на `/app/trips`) |
| `.запомнить`, `.забыть` | `RemindService` |
| `.викторина` | `QuizService` |
| `.связь` | `FeedbackService` |
| `.следить` | `InterestedService` |
| `.предсказание` | `PredictionService` |
| `.вчат` | `SendToChatService` |
| `.статус` | `UserStatusService` (статус в канале поездок через `getChatMember`) |

Новая команда = `BotCommands` + `@Service implements CommandExecutable` (подхватывается
автоматически) + строка в `HelpService` + тест.

## Клавиатуры

`KeyboardUtils.getKeyboardMessage(...)` — inline-кнопка **`web_app`** (открывает Mini
App с `initData`). **Работает только в личных чатах**; в группах/каналах Telegram
вернёт `BUTTON_TYPE_INVALID`.

Поэтому в чаты/каналы: либо обычный текст со ссылкой (кликабельна), либо inline-кнопка
`url` (`InlineKeyboardButton.setUrl`). Пример: сообщение о бане в канал поездок —
текст + ссылка на жалобу.

## Реклама и Telegram Stars

- `CreateInvoiceLink` с валютой `XTR` и **пустым** `provider_token`.
- Библиотека 6.9.x не принимает пустой токен → используется наследник
  `StarsInvoiceLink` с переопределённым `validate()`.
- Поток: предложение (`PENDING`) → одобрение админом (`APPROVED`) → оплата (`ACTIVE`,
  `payment_charge_id`, `stars_paid`) → показ по расписанию (`Job.adsWeekdays/adsWeekends`)
  → сверка через `reconcileAds`.
- Предоплата подтверждается в `AdPaymentService.confirmPreCheckout`, факт оплаты —
  по `successful_payment` в `processMessage`.
- Объявление: до 3 вложений, 100 ⭐/день, только в чатах с `enable_ads`.

## Жалобы, баны, уведомления

- Жалоба: `feedback` (+ вложения `{upload-dir}/{feedbackId}`), переписка — `feedback_chat`
  (вложения `fc-{commentId}`), `close_reason`, `closed_at`, `target_user_id`.
- Бан из Mini App: `banChatMember` → запись в `banned_user` (+ `feedback_id`) →
  сообщение в канал поездок со ссылкой на жалобу → жалоба закрывается.
- `BanNotificationService`: если пишет забаненный — предупреждение в личку не чаще раза
  в час; ссылка на жалобу добавляется только при наличии `feedback_id`.
- У жалоб есть публичный просмотр (см. `miniapp.md`), у банов — только админ-список.

## Ограничения Bot API (не тратить время)

| Чего нет | Следствие |
|---|---|
| Списка забаненных участников | `banned_user` ведётся в БД; импорт — вручную/миграцией |
| Событий удаления сообщений | «удалено» ставим только когда бот удалил сам (фильтр слов) |
| `web_app`-кнопок в группах/каналах | в чаты — текст или `url`-кнопка |
| Доступа к истории чата | бот видит только новые сообщения |

Правки сообщений **есть** (`edited_message`), у них `message.getDate()` — время
отправки исходного сообщения, `message.getEditDate()` — время правки.

## Ведение лога сообщений

- `log` — последняя версия сообщения; `message_id` (Telegram id), `edited_at`,
  `deleted`.
- `log_edit` — предыдущие версии (`log_id`, `text`, `filetype`, `file`, `edited_at`).
- Правка: старая версия в `log_edit`, новая — в `log`. Если строка не найдена по
  `message_id` (сообщение сохранено до V15), ищем по `(chat_id, user_id, date_time)`
  и проставляем `message_id`.
- Удаление ботом помечает `deleted = true` (`LogService.markDeleted`).
- В истории (`/app/chat/{id}`) и на `/app/trips` показываются бейджи и кнопка
  «История изменений (N)».

## Фильтр слов

`WordsFilterService.deleteByFilter`: удаляет сообщения с запрещёнными словами и (в
канале поездок) со ссылками. Не трогает супер-админа, админов чата и анонимных админов
(`GroupAnonymousBot`, `senderChat`). После удаления ставит пометку в лог.
