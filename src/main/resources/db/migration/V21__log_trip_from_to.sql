-- Переходим от одного направления к отдельным точкам отправления и прибытия.
-- Старые распознанные направления переносим в trip_from/trip_to.

alter table log add column if not exists trip_from varchar(100);
alter table log add column if not exists trip_to varchar(100);

do $$
begin
    if exists (select 1
               from information_schema.columns
               where table_schema = current_schema()
                 and table_name = 'log'
                 and column_name = 'trip_direction') then
        update log
        set trip_from = 'Чебоксары', trip_to = 'Урмары'
        where trip_direction = 'CHEBOKSARY_URMARY'
          and trip_from is null
          and trip_to is null;

        update log
        set trip_from = 'Урмары', trip_to = 'Чебоксары'
        where trip_direction = 'URMARY_CHEBOKSARY'
          and trip_from is null
          and trip_to is null;
    end if;
end
$$;

drop index if exists log_trip_direction_idx;
alter table log drop column if exists trip_direction;

create index if not exists log_trip_route_idx
    on log (chat_id, trip_from, trip_to, trip_time);
