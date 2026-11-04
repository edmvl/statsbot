-- Количество свободных мест в поездке (необязательное поле).
-- Старые поездки переразбираем один раз в фоне, чтобы заполнить значение.

do $$
begin
    if not exists (select 1
                   from information_schema.columns
                   where table_schema = current_schema()
                     and table_name = 'log'
                     and column_name = 'trip_seats') then
        alter table log add column trip_seats integer;

        update log
        set trip_parsed = false
        where trip_time is not null
          and (trip_from is not null or trip_to is not null);
    end if;
end
$$;
