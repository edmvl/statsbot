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

`TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
Имеют приоритет над файлами.

Если профильный файл не найден, берутся запасные значения из
`src/main/resources/application.properties`. При пустом токене приложение упадёт
на старте — это защита от запуска «не тем ботом».

## Разработка

Локальная база (Postgres + дамп из `tgbot_dump`):

```
docker compose up -d
```

Тесты:

```
mvn -o test
```

## Безопасность

Токен бота, который раньше лежал в `pom.xml`, попал в **историю git** — простого
удаления из файлов недостаточно. Обязательно перевыпустите его: `@BotFather` → `/revoke`
→ новые значения в `config/application-local.properties` и `config/application-prod.properties`.
По желанию вычистить историю (`git filter-repo` / BFG), но перевыпуск токена обязателен
в любом случае.

## Миграции

Схема БД ведётся Flyway (`src/main/resources/db/migration`) и применяется автоматически
при старте приложения.
