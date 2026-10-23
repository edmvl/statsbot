# База данных, сущности, миграции

## Flyway

- Миграции: `src/main/resources/db/migration`, имена `V{n}__snake_case.sql`
  (`V3.1` тоже есть — out-of-order разрешён).
- Настройки: `baseline-on-migrate=true`, `baseline-version=0`, `out-of-order=true`,
  `placeholder-replacement=false`.
- Применяются автоматически при старте. `ddl-auto` не задан → Hibernate **ничего не
  создаёт**; схема только из миграций (сущности должны совпадать с ней).
- SQL — PostgreSQL. Писать идемпотентно:

```sql
alter table log add column if not exists message_id int;
create index if not exists log_chat_message_idx on log (chat_id, message_id);
create table if not exists log_edit (...);
```

- Заполнение старых строк — отдельным `update ... where ... is null` перед `set not null`.
- Массовые вставки — через `with ... (values (...), (...)) insert ... select ...`,
  с защитой от дублей: `where not exists (select 1 from ...)`.

## Дампа нет в репозитории

`db_dump.bak` (plain SQL ~290 МБ) в `.gitignore`; `docker-compose.yaml` монтирует его
в `/docker-entrypoint-initdb.d`. `tgbot_dump/` — пустая папка-наследие.

Локальная БД:

```powershell
docker compose up -d          # Postgres 12 + инициализация дампом (если файл есть)
docker compose down -v        # снести вместе с данными
```

`docker-compose` создаёт том только при первом старте; `docker rm` контейнера данные
не сохраняет — после пересоздания нужен дамп заново (~2 минуты).

## Таблицы (основное)

| Таблица | Назначение |
|---|---|
| `users` | пользователи: имя, ник, аватар (`avatar_path`), `user_name_history` — история смен |
| `log` | сообщения: `chat_id`, `date_time`, `user_id`, `chat_name`, `text`, `filetype`, `file`, `logger_type`, **`message_id`**, **`edited_at`**, **`deleted`** |
| `log_edit` | предыдущие версии сообщений (`log_id`, `text`, `filetype`, `file`, `edited_at`) |
| `chat_settings` | настройки чата (`setting_name`/`setting_value`), в т.ч. `trip_channel`, `enable_ads`, `enable_stats`, `enable_member_control` |
| `settings` | админы чатов (`chat_id`, `user_id`, `is_admin`) |
| `chat_member` | участники и активность |
| `rating` | отзывы (звёзды + комментарий) о пользователях |
| `interested` | подписки на авторов |
| `ads`, `ads_files` | реклама и вложения (статусы `ACTIVE/PENDING/APPROVED/REJECTED`) |
| `feedback` | жалобы (`target_user_id`, `close_reason`, `closed_at`, `finished`) |
| `feedback_chat` | переписка по жалобе |
| `banned_user` | баны: `user_id`, `chat_id`, `user_name`, `feedback_id`, `banned_by`, `banned_at` |
| `words_filter`, `remind`, `quiz`, `challenge*`, `selected`, `horo`, `prediction` | остальные механики |

## Соглашения JPA

- `@Entity @Data`, `@Table(name = "snake_case")`, `@Column(name = "snake_case")`.
- id — `@GeneratedValue(strategy = IDENTITY)`.
- Для страниц — **интерфейсные проекции** (`ChatMessageDto`, `TripViewDto`,
  `ChatViewDto`), алиасы в native-запросах: `l.date_time as dateTime`.
  Postgres отдаёт алиасы в нижнем регистре (`datetime`), Spring Data сопоставляет
  их с геттерами без учёта регистра — это нормально.
- Проекции с булевыми/числовыми полями — `Boolean getDeleted()`, `Integer getMessageId()`;
  `null` возможен, в шаблоне проверяй.
- `findFirstByChatIdAndMessageId(...)` — derived-запрос для поиска строки лога.

## Как проверить миграцию на живом Postgres (без дампа)

Docker должен быть запущен (Docker Desktop, сервис `com.docker.service`).

```powershell
docker run --rm -d --name pg_test -e POSTGRES_PASSWORD=postgres -p 55433:5432 postgres:14
# дождаться готовности
docker exec pg_test pg_isready -U postgres

# минимальная схема под миграцию (создать руками нужные таблицы и 1-2 строки)
# затем — копировать файл ВНУТРЬ контейнера (docker cp), а не лить через PowerShell-пайп:
docker cp "src/main/resources/db/migration/V15__log_message_edits.sql" pg_test:/tmp/m.sql
docker exec pg_test psql -U postgres -v ON_ERROR_STOP=1 -f /tmp/m.sql

# проверить данные/идемпотентность: прогнать -f повторно, посмотреть count/структуру
docker exec pg_test psql -U postgres -c "\d log"
docker rm -f pg_test
```

**Почему `docker cp`:** пайп `Get-Content | docker exec -i psql` в PowerShell 5.1
превращает кириллицу в `?` (неверная `$OutputEncoding`). Для ASCII-only SQL пайп ок.

## Проверка запросов

Валидируй не только миграцию, но и изменённые native-запросы: вставь пару строк,
выполни запрос с `\gdesc` (покажет имена колонок и типы) — так ловятся опечатки
в алиасах и несовпадение типов.

## Импорт данных

Пример массового импорта — `V16__import_banned_users.sql`: 101 забаненный из чёрного
списка чата, `banned_by = 'чёрный список'`, `feedback_id = null`, `chat_id` берётся
подзапросом из `chat_settings`. Такие импорты делать идемпотентными.
