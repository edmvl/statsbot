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

Правило: **зелёные тесты обязательны** перед завершением задачи. Сейчас их 186
(`mvn -o test` → `Tests run: 186, Failures: 0`).

Запуск приложения (локально, без сети до Telegram):

```powershell
& $mvn -o clean package
java -jar target/messageStatBot-0.0.1-SNAPSHOT.jar --telegram.bot.token=1:fake `
  --telegram.bot.username=testbot `
  --spring.autoconfigure.exclude=org.telegram.telegrambots.starter.TelegramBotStarterConfiguration
```

## Миграции: локальная проверка

Прод-схему и запросы к ней проверяем **локально**, на копии прод-дампа в Docker.
`db_dump.bak` (в `.gitignore`) нужен только для первой заливки — ~2 минуты, дальше
том переиспользуется, старт секунды. Нужен запущенный Docker Desktop.

```bash
scripts/test-migration.sh                # поднять базу tgbot с дампом
scripts/test-migration.sh src/main/resources/db/migration/V27__selected.sql
scripts/test-migration.sh $(ls src/main/resources/db/migration/V{18,19,20,21,22,23,24,25,26,27}__*.sql)
```

Что проверять для новой миграции: применение без ошибок (`ON_ERROR_STOP=1`),
повторный запуск (идемпотентность), ограничения вставкой/удалением строк,
нужные индексы (`select indexname from pg_indexes where tablename = '...'`).
Пересоздать чистую: `docker rm -f statsbot-pgtest && docker volume rm statsbot-pgtest-data`.

## Жёсткие правила

- **Секреты только в `config/`** (в `.gitignore`) или в env. В репозитории — только
  `*.example.properties`. Токен/пароль в коде, тестах, логах и коммитах — запрещено.
- **Пользователя берём только из подписанного Telegram `initData`**
  (`CurrentUserService`), никогда из query/body (`userId=...` из клиента — дыра).
- **Права проверяются на сервере**: супер-админ `115965184`; админы канала поездок —
  `settingsService.isUserAdmin(tripChannelId, userId)`, канал берётся из
  `chat_settings` (`trip_channel=true`), не из константы.
- **У бина Spring — ровно один конструктор** (либо один помечен `@Autowired`).
  Моки в тестах такого не ловят, а прод падает на старте с `NoSuchMethodException`
  (инцидент `dbd251c`, 15.11.2026: сервис не поднимался 5 минут). Страховка —
  `SpringBeanConstructorTest`, он в общем наборе.
- **Схема БД только через Flyway** (`src/main/resources/db/migration/V{n}__*.sql`),
  идемпотентно (`if not exists`), с проверкой на локальном Postgres в Docker
  (`scripts/test-migration.sh`) — **на проде SQL не экспериментировать**.
- **Прод — не песочница.** Для тестов (SQL, миграции, запросы, скретч-базы) не ходи
  на сервер вообще: поднимай локальный Postgres в Docker. На прод — только логи
  (см. «Прод и логи») и факт того, что миграция применилась после деплоя.
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
не пытаться вводить пароль. Частые подряд `ssh` упираются в лимит соединений — делать
паузу 40–60 с и не паниковать.
Pull request’ы и коммиты — как обычно, `git push` уходит через credential helper
без интерактива.

## Безопасность сервера

Сделано (15.11.2026):
- `fail2ban` (jail `sshd`, `maxretry=5`, `findtime=30m`, `bantime` с инкрементом до 4 недель)
  — основная защита от перебора паролей; `ufw limit` для 22 порта снят, потому что
  GitHub Actions открывает 6+ соединений подряд и деплой падал.
- `ufw`: `deny incoming` по умолчанию, открыты только 22/80/443; 8081 и 5432 снаружи закрыты.
- sshd: `/etc/ssh/sshd_config.d/10-hardening.conf` — `MaxAuthTries 4`, `LoginGraceTime 20`,
  без X11/agent forwarding. `PasswordAuthentication no`, `PermitRootLogin prohibit-password`
  — вход только по ключу (`moba_root` у пользователя в MobaXterm, `pi_logs` у pi на `deploylogs`).
  Если пользователь потерял ключ — только консоль в панели Aeza.
- `unattended-upgrades` + `apt-daily.timer`/`apt-daily-upgrade.timer` (были masked) —
  автоустановка security-обновлений, авто-ребут выключен. Побочка: `needrestart`
  перезапускает `ssh.service` после обновления libpam/openssh, живые SSH-сессии рвутся
  (MobaXterm показывает «Session stopped» — лечится кнопкой R).
- Пароль Postgres `postgres` ротирован; новый в `/home/config/application-prod.properties`,
  копия в `/root/.pgpw.new` (600).
- Сломанный PPA `linuxuprising/java` и дубликат `pgdg.list` отключены.

Не делать: не возвращать `ufw limit OpenSSH`, не включать парольный вход в sshd, не
отключать fail2ban — деплой идёт с ключа, а перебор идёт постоянно.

## Перед ответом пользователю

1. `mvn -o test` — зелёный.
2. Если менял вёрстку/JS — отрендери страницу и посмотри глазами
   (рецепт в скилле: `references/debugging.md`, раздел «Визуальная проверка»).
3. Если менял SQL — прогони миграцию на временном Postgres в Docker.
4. Кратко перечисли, что изменено, чем проверено и какие есть ограничения.
