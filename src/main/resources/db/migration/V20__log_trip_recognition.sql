-- Распознанные параметры поездки для сообщений канала попутчиков.
-- Заполняется приложением только при сохранении/редактировании сообщения,
-- поэтому на чтение истории дополнительной нагрузки нет.

alter table log add column if not exists trip_direction varchar(32);
alter table log add column if not exists trip_time timestamp;

create index if not exists log_trip_direction_idx
    on log (chat_id, trip_direction, trip_time);
