-- Отметка «сообщение уже проверено распознаванием поездки».
-- Нужна для фонового разбора старых сообщений: каждое обрабатываем один раз,
-- в том числе если направление/время не распознались.

alter table log add column if not exists trip_parsed boolean not null default false;

-- Уже распознанные сообщения повторно не разбираем.
update log
set trip_parsed = true
where trip_from is not null
   or trip_to is not null
   or trip_time is not null;

create index if not exists log_trip_pending_idx
    on log (chat_id, trip_parsed);
