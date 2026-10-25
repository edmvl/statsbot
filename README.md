# messageStatBot

Telegram-бот статистики чатов и мини-приложение (Thymeleaf) для отзывов о попутчиках,
подписок на авторов, рекламы с оплатой звёздами и админских страниц.

## Окружения и секреты

В репозитории **нет** токенов и паролей. Окружение выбирается профилем Spring:

| Профиль | Файл с реальными значениями | Как включается |
|---|---|---|
| `local` | `config/application-local.properties` | по умолчанию |
| `prod` | `config/application-prod.properties` | `SPRING_PROFILES_ACTIVE=prod` |

Папка `config/` в `.gitignore` — реальные значения в публичный репозиторий не попадают.
Шаблоны без секретов лежат в репозитории: `application-local.example.properties`,
`application-prod.example.properties`.

### Первичная настройка

```
cp application-local.example.properties config/application-local.properties
cp application-prod.example.properties  config/application-prod.properties
# заполнить реальными токенами и паролями (обе среды — чтобы ничего не потерялось)
```

> Не копируйте папку `config/` с машины разработчика на сервер — на бою должен лежать
> только `config/application-prod.properties`.

### Запуск

Локально (профиль `local` включён по умолчанию, `config/` берётся из рабочего каталога):

```
mvn -o spring-boot:run
# или
mvn -o clean package && java -jar target/messageStatBot-0.0.1-SNAPSHOT.jar
```

Прод:

```
SPRING_PROFILES_ACTIVE=prod java -jar /home/edmvl/messageStatBot.jar
```

systemd:

```ini
[Service]
Environment=SPRING_PROFILES_ACTIVE=prod
WorkingDirectory=/home/edmvl
ExecStart=/usr/bin/java -jar /home/edmvl/messageStatBot.jar
```

### Переменные окружения (альтернатива файлам)

`TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`YANDEX_MAPS_API_KEY` (карта в квестах; можно не задавать).
Имеют приоритет над файлами.

Если профильный файл не найден, берутся запасные значения из
`src/main/resources/application.properties`. При пустом токене приложение упадёт
на старте — это защита от запуска «не тем ботом».

## Разработка

Локальная база (Postgres + дамп `db_dump.bak`, он в `.gitignore`):

```
docker compose up -d
```

Тесты:

```
mvn -o test
```

## База знаний проекта (для людей и ИИ-агентов)

Чтобы знания не терялись между сессиями и машинами, всё важное лежит в репозитории:

- `AGENTS.md` — короткие правила и команды, читаются автоматически в каждой сессии.
- `.opencode/skills/statsbot-dev/` — скилл с деталями: архитектура, авторизация
  Mini App, обработка апдейтов бота, миграции, подводные камни и чек-листы
  (`references/*.md`).

Перед крупной задачей: `skill(id="statsbot-dev")` (или просто попроси агента
«загрузи скилл statsbot-dev»). Дополняй эти файлы, когда узнаёшь новое — они
уезжают вместе с проектом на любую машину.

## Миграции

Схема БД ведётся Flyway (`src/main/resources/db/migration`) и применяется автоматически
при старте приложения.
