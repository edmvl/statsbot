#!/usr/bin/env bash
# Локальный прогон SQL-миграций на копии прод-дампа в Docker.
# Прод-сервер для тестов не используется: сюда только читаем db_dump.bak.
#
# Использование:
#   scripts/test-migration.sh                                  # только поднять базу с дампом
#   scripts/test-migration.sh src/main/resources/db/migration/V27__selected.sql
#   scripts/test-migration.sh src/main/resources/db/migration/V2*.sql
#
# Контейнер statsbot-pgtest и том statsbot-pgtest-data переиспользуются:
# дамп заливается один раз (~2 минуты), дальше старт занимает секунды.
# Пересоздать с нуля: docker rm -f statsbot-pgtest && docker volume rm statsbot-pgtest-data
set -euo pipefail

cd "$(dirname "$0")/.."

CONTAINER=${CONTAINER:-statsbot-pgtest}
VOLUME=${VOLUME:-statsbot-pgtest-data}
IMAGE=${IMAGE:-postgres:17}
DB=${DB:-tgbot}
TEST_PASSWORD=test
DUMP=${DUMP:-db_dump.bak}

if [ ! -f "$DUMP" ]; then
  echo "!! нет дампа $DUMP (он в .gitignore, качается с прода один раз)" >&2
  exit 1
fi

if ! docker container inspect "$CONTAINER" >/dev/null 2>&1; then
  echo ">> создаю контейнер $CONTAINER ($IMAGE)"
  docker run -d --name "$CONTAINER" \
    -e "POSTGRES_PASSWORD=$TEST_PASSWORD" \
    -v "$VOLUME":/var/lib/postgresql/data \
    -v "$PWD":/repo:ro \
    "$IMAGE" >/dev/null
fi

docker start "$CONTAINER" >/dev/null
echo -n ">> жду postgres"
for _ in $(seq 1 60); do
  if docker exec "$CONTAINER" pg_isready -U postgres -q; then break; fi
  echo -n "."
  sleep 2
done
echo " готов"

psql_srv() { docker exec -i "$CONTAINER" psql -U postgres -v ON_ERROR_STOP=1 "$@"; }

if [ "$(psql_srv -d postgres -tAc "select 1 from pg_database where datname = '$DB'")" != "1" ]; then
  echo ">> первичная заливка $DUMP в базу $DB (один раз, ~2 минуты)"
  # Роль-владелец схемы прода, иначе на каждом ALTER ... OWNER TO tgbot будет ERROR.
  psql_srv -q -d postgres -c "do \$\$ begin if not exists (select 1 from pg_roles where rolname = 'tgbot') then create role tgbot login; end if; end \$\$"
  psql_srv -q -d postgres -c "create database $DB owner tgbot"
  docker exec -i "$CONTAINER" psql -U postgres -q -d "$DB" < "$DUMP" > /dev/null
fi

echo ">> база $DB: $(psql_srv -d "$DB" -tAc "select count(*) from log") строк в log, версия схемы $(psql_srv -d "$DB" -tAc "select coalesce((select version from flyway_schema_history order by installed_rank desc limit 1), 'нет')")"

for file in "$@"; do
  echo ">> применяю $file"
  docker exec -i "$CONTAINER" psql -U postgres -q -v ON_ERROR_STOP=1 -d "$DB" -f - < "$file"
  echo "   ok"
done

echo ">> готово. Подключиться: docker exec -it $CONTAINER psql -U postgres -d $DB"
