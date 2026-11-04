-- Одна поездка может быть опубликована несколько раз с разницей во времени
-- до 15 минут и разным количеством мест. Помечаем повторные сообщения ссылкой
-- на основное (самое свежее) сообщение группы.

do $$
begin
    if not exists (select 1
                   from information_schema.columns
                   where table_schema = current_schema()
                     and table_name = 'log'
                     and column_name = 'trip_duplicate_of') then
        alter table log add column trip_duplicate_of bigint;

        -- Заставляем фоновый сервис один раз пересобрать группы старых поездок.
        update log
        set trip_parsed = false
        where trip_time is not null
          and (trip_from is not null or trip_to is not null);
    end if;
end
$$;

create index if not exists log_trip_duplicate_idx
    on log (chat_id, trip_duplicate_of);
