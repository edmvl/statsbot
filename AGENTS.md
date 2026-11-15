# messageStatBot — инструкции для агента

Telegram-бот статистики чатов + Mini App на Thymeleaf (Java 16, Spring Boot 2.5.3, Postgres,
Flyway, telegrambots 6.9.7.1). Рабочая копия обычно на Windows, прод — Linux + systemd.

**Глубокая база знаний — скилл `statsbot-dev`** (`.opencode/skills/statsbot-dev/`).
Перед любой нетривиальной задачей загрузи его: `skill(id="statsbot-dev")`.
Там архитектура, все подводные камни, рецепты «как добавить страницу/команду/миграцию»
и способы отладки. Не изобретай заново — сверься.

## Сборка и тесты

Windows (PowerShell). Пути ниже — с конкретной машины; на другой машине подставь свои
(`mvn` из PATH/IntelliJ, `JAVA_HOME` из `java -version` — см. `references/debugging.md`):

```powershell
$env:JAVA_HOME="C:\Users\info\.jdks\ms-17.0.17"
$mvn="C:\Program Files\JetBrains\IntelliJIdea2026.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
& $mvn -o test                                        # весь набор
& $mvn -o test "-Dtest=LogServiceTest,ProcessorTest"  # только классы (через запятую!)
```

Правило: **зелёные тесты обязательны** перед завершением задачи. Сейчас их ~100
(`mvn -o test` → `Tests run: 100, Failures: 0`).

Запуск приложения (локально, без сети до Telegram):

```powershell
& $mvn -o clean package
java -jar target/messageStatBot-0.0.1-SNAPSHOT.jar --telegram.bot.token=1:fake `
  --telegram.bot.username=testbot `
  --spring.autoconfigure.exclude=org.telegram.telegrambots.starter.TelegramBotStarterConfiguration
```

## Жёсткие правила

- **Секреты только в `config/`** (в `.gitignore`) или в env. В репозитории — только
  `*.example.properties`. Токен/пароль в коде, тестах, логах и коммитах — запрещено.
- **Пользователя берём только из подписанного Telegram `initData`**
  (`CurrentUserService`), никогда из query/body (`userId=...` из клиента — дыра).
- **Права проверяются на сервере**: супер-админ `115965184`; админы канала поездок —
  `settingsService.isUserAdmin(tripChannelId, userId)`, канал берётся из
  `chat_settings` (`trip_channel=true`), не из константы.
- **Схема БД только через Flyway** (`src/main/resources/db/migration/V{n}__*.sql`),
  идемпотентно (`if not exists`), с проверкой на живом Postgres.
- **Не перезаписывай файлы через PowerShell** (`Get-Content | Set-Content`) — ломает
  UTF-8 и кириллицу. Только инструменты `edit`/`write`.
- **Темы**: страницы Mini App обязаны работать в светлой и тёмной теме Telegram —
  только переменные из `static/css/theme.css`, без хардкода цветов.
- Комментарии и тексты интерфейса — по-русски, в стиле окружающего кода.

## Что где лежит

| Область | Путь |
|---|---|
| Mini App контроллеры | `src/main/java/.../rest/` |
| Бизнес-логика | `src/main/java/.../service/` |
| Телеграм-обработка | `handler/`, `processor/`, `message_executor/` |
| Сущности и репозитории | `entity/`, `repo/` |
| Шаблоны | `src/main/resources/templates/*.html` |
| Общая тема и JS | `static/css/theme.css`, `static/js/app-back.js` |
| Миграции | `src/main/resources/db/migration/` |
| Тесты | `src/test/java/...` (рендер шаблонов — `TemplateRenderTest`) |

## Прод и логи

Деплой — GitHub Actions по пушу в `main` (`.github/workflows/deploy.yml`, systemd-сервис `tgbot`).
Руками не деплоить, сервис не перезапускать, на сервере ничего не менять.

Логи (только чтение):

```bash
ssh statsbot 'systemctl status tgbot --no-pager'
ssh statsbot 'journalctl -u tgbot -n 300 --no-pager'
ssh statsbot 'journalctl -u tgbot -p err --since "1 hour ago" --no-pager'
```

Alias `statsbot` = `deploylogs@api.zhendozzz.ru`, ключ `~/.ssh/pi_logs` (в `authorized_keys`
с опцией `restrict`: без pty, forwarding, agent и X11), беспарольный. Пользователь
`deploylogs` состоит в группе `systemd-journal`, sudo нет — только чтение логов.
Перезапуск/правка файлов на проде из этой сессии невозможны: изменения — через пуш в `main`.

Админский alias `statsbot-root` (`root@…`, ключ `~/.ssh/moba_root` — тот же, что импортируется
в MobaXterm пользователя) использовать только с явного разрешения пользователя.

Если `ssh` просит пароль или `yes/no` — ключ не установлен: сказать пользователю,
не пытаться вводить пароль. Частые подряд `ssh` упираются в `ufw limit OpenSSH`
(6 коннектов / 30 с, таймаут соединения) — делать паузу 40–60 с и не паниковать.
Pull request’ы и коммиты — как обычно, `git push` уходит через credential helper
без интерактива.

## Перед ответом пользователю

1. `mvn -o test` — зелёный.
2. Если менял вёрстку/JS — отрендери страницу и посмотри глазами
   (рецепт в скилле: `references/debugging.md`, раздел «Визуальная проверка»).
3. Если менял SQL — прогони миграцию на временном Postgres в Docker.
4. Кратко перечисли, что изменено, чем проверено и какие есть ограничения.
