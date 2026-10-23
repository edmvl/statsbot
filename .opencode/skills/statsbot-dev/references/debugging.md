# Отладка: подводные камни и рецепты проверки

## Кириллица и кодировки (Windows)

**Не перезаписывай исходники через PowerShell.** Конструкция
`(Get-Content file) -replace ... | Set-Content -Encoding UTF8` в PowerShell 5.1 читает
файл в системной ANSI-кодировке и превращает кириллицу в мусор (+ добавляет BOM).
Правь файлы инструментами `edit`/`write`.

Если всё же испортил — обратное преобразование (ACP = 1251):

```powershell
$p = "путь\файл"
$m = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
if ($m.StartsWith([string][char]0xFEFF)) { $m = $m.Substring(1) }
$bytes = [System.Text.Encoding]::GetEncoding(1251).GetBytes($m)
$text  = [System.Text.Encoding]::UTF8.GetString($bytes).Replace("`r`n", "`n")
[System.IO.File]::WriteAllText($p, $text, (New-Object System.Text.UTF8Encoding($false)))
```

**Консоль врёт.** Русский текст в `Get-Content`, `psql`, `mvn` часто выглядит как
`?????` / `�?��` — это кодовая страница консоли, а не файл. Проверяй содержимое так:

```powershell
$t = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
$t.Contains("Переписка")            # True/False — надёжнее вывода
# и первые байты: 239,187,191 = BOM (в шаблонах его быть не должно)
[System.IO.File]::ReadAllBytes($p)[0..2] -join ','
```

## Thymeleaf / SpEL

- `!null` в SpEL кидает `EL1001E` — пиши `attr != null and attr`, `canEdit == false`.
- `#lists.size(null)` кидает исключение → `attr != null and !#lists.isEmpty(attr)`.
- Обращение к отсутствующей в модели переменной даёт `null` (не ошибку), но вызов
  метода на ней — ошибку.
- `th:if` и `th:text` на одном элементе можно: сначала отрабатывает `th:if`.
- `th:attr="data-x=${cond ? 'v' : null}"` — при `null` атрибут просто не рендерится
  (так делается `data-back` только для авторизованных).
- Даты: `#temporals.format(date, 'dd.MM.yyyy HH:mm')`, локаль — `ruLocale` в модели.
- Числа/строки из native-запросов приходят как `BigDecimal`/`Long` — сравнивай
  аккуратно (`trip.rating >= 4` работает).
- `TemplateRenderTest` использует **`SpringTemplateEngine`** (SpEL), как в проде:
  обычный `TemplateEngine` на OGNL скрывает часть ошибок. Новые атрибуты модели
  добавляй в тест или делай шаблон null-safe.

## Скрытие элементов

В `theme.css` есть `[hidden] { display: none !important; }`. Скрывай/показывай
**атрибутом** `hidden` (`el.hidden = true/false`, `removeAttribute('hidden')`).
Через `style.display = 'none'` нельзя: `.menu-card { display: flex }` перебьёт
инлайн-стиль, и кнопка останется невидимой, но кликабельной (уже случалось).

## «Не видно изменений» и другие симптомы

| Симптом | Куда смотреть |
|---|---|
| Правки сообщений не появляются | `Processor.process` — гейт `hasMessage() || hasEditedMessage()` |
| Не сохраняется удаление | Bot API не присылает удаления; `markDeleted` вызывается только из фильтра |
| `Доступ только через Telegram` | сработал `AdminAuthGuard.writeBootstrap`: на странице нет `initData` (открыли в браузере) |
| 403 на админ-странице | нет/битая cookie, не админ; проверь `/app/access` и `getVerifiedUserId` |
| Кнопка «неактивна» | JS упал до `saveBtn.disabled = false` — смотри консоль/элемент |
| Невидимые, но кликабельные кнопки | `[hidden]` + `display:flex` (см. выше) |
| Ссылка из чата ведёт в никуда | ссылки на жалобы открываются и без `initData` (режим чтения) |
| Пустые списки в Mini App | БД без дампа или не тот `chat_id`/`trip_channel` |

## Визуальная проверка без запуска приложения

Самый быстрый способ увидеть страницу с данными (использовался много раз):

1. Временный тест `PreviewDumpTest` в `src/test/java/...`: поднять `SpringTemplateEngine`
   (как в `TemplateRenderTest`), замокать DTO через Mockito, отрендерить шаблон и
   записать HTML в `preview/<page>.html`. Заодно заменить ссылки:
   `"/css/theme.css"` → `/src/main/resources/static/css/theme.css`,
   `telegram-web-app.js` / `app-back.js` → `about:blank`; можно подменить `window.fetch`
   заглушкой, если страница что-то грузит.
2. `python -m http.server 8766 --bind 127.0.0.1` из корня репозитория (в фоне).
3. Открыть `http://localhost:8766/preview/<page>.html` в браузере и сделать скриншот
   (`browser.tabs.open` + `browser.screenshot`, лучше `fullPage: true`).
4. Проверить интерактив через `browser.evaluate` (например, клик по кнопке и
   `JSON.stringify({disabled: btn.disabled})`).
5. Удалить `preview/` и временный тест, остановить сервер.

## Запуск приложения локально

Нужна БД (иначе Flyway/страницы упадут) и заглушка токена, чтобы не ходить в Telegram:

```powershell
& $mvn -o clean package
java -jar target/messageStatBot-0.0.1-SNAPSHOT.jar --telegram.bot.token=1:fake `
  --telegram.bot.username=testbot `
  --spring.autoconfigure.exclude=org.telegram.telegrambots.starter.TelegramBotStarterConfiguration
```

Порт — `8081`. Сеть до `api.telegram.org` может быть недоступна (в песочнице её нет):
тогда уведомления не уходят, но записи в БД и страницы работают.

Тестовый `initData` (cookie `tg_webapp_auth` / заголовок `X-Telegram-Init-Data`):
`secret = HMAC_SHA256("WebAppData", token)`, `hash = HMAC_SHA256(secret, "auth_date=..\nuser=<json>")`,
поля — по алфавиту, `hash` последним; `user` — JSON с `id`.

## Maven

```powershell
$env:JAVA_HOME="C:\Users\info\.jdks\ms-17.0.17"
$mvn="C:\Program Files\JetBrains\IntelliJIdea2026.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
& $mvn -o test
& $mvn -o test "-Dtest=LogServiceTest,TemplateRenderTest"   # через запятую, не через '+'
```

`-o` — офлайн-режим (зависимости уже в `~/.m2`). `BUILD SUCCESS` + `Tests run: N, Failures: 0`.

**Новая машина:** если этих путей нет, найди свои —

```powershell
where.exe mvn            # если mvn в PATH
java -version            # понять версию JDK
Get-ChildItem "$env:USERPROFILE\.jdks"                  # JDK из IntelliJ
Get-ChildItem "C:\Program Files\JetBrains" -Directory   # IDE и встроенный maven
```

Проект собирается на JDK 16+ (в `pom.xml` `java.version=16`, CI использует 17).
Если `mvn` не в PATH, бери `...\plugins\maven-plugin\lib\maven3\bin\mvn.cmd` из IntelliJ.

## Docker

- Если `docker` пишет `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`
  — не запущен Docker Desktop (`Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"`,
  ждать ~15–40 сек).
- `docker cp` + `psql -f` для UTF-8 SQL; пайп PowerShell для кириллицы не годится.
- Для проверки миграций поднимай **одноразовый** контейнер (`--rm -d`, порт 55433+),
  создавай минимальную схему руками, в конце `docker rm -f`.

## Логи

- Прод: `journalctl -u tgbot -n 100 --no-pager`, статус `systemctl status tgbot`.
- В приложении много `log.error` в `MessageExecutorImpl` (не удалось отправить/удалить) —
  при отсутствии сети это нормально.
