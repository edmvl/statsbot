-- Восстанавливаем историю смены ФИО/ника из уже существующих данных в log:
-- для каждого пользователя берём последовательность по времени и записываем
-- отдельную запись на каждое изменение имени. Версия V3.1 — чтобы выполнилось
-- ДО удаления колонок из log (миграция V4).
--
-- Если колонки в log уже удалены (миграция V4 применена ранее), шаг ничего не делает —
-- данные восстановить нельзя, остаётся только снимок из V3, а дальше история пишется
-- приложением при сообщениях.

do $$
begin
    if exists (select 1
               from information_schema.columns
               where table_schema = current_schema()
                 and table_name = 'log'
                 and column_name = 'user_first_name') then

        delete from user_name_history;

        insert into user_name_history (user_id, first_name, last_name, user_name, changed_at)
        with ordered as (
            select user_id,
                   user_first_name,
                   user_last_name,
                   user_name,
                   date_time,
                   lag(user_first_name) over w as prev_first,
                   lag(user_last_name)  over w as prev_last,
                   lag(user_name)       over w as prev_user
            from log
            where user_id is not null and user_id <> '' and user_id ~ '^[0-9]+$'
            window w as (partition by user_id order by id)
        )
        select user_id, user_first_name, user_last_name, user_name, date_time
        from ordered
        where prev_first is distinct from user_first_name
           or prev_last  is distinct from user_last_name
           or prev_user  is distinct from user_name;
    end if;
end
$$;
