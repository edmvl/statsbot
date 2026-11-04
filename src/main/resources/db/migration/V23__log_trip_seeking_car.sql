-- Признак «автор ищет машину/попутку».
-- Старые сообщения помечаем неразобранными, чтобы фоновый сервис один раз
-- проставил новый признак по тем же правилам, что и для новых сообщений.

alter table log add column if not exists trip_seeking_car boolean;

update log
set trip_parsed = false
where trip_seeking_car is null;

create index if not exists log_trip_seeking_idx
    on log (chat_id, trip_seeking_car);
