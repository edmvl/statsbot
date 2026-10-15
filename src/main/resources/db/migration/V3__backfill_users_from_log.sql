-- Разовый перенос исторических пользователей из log в users и стартовая запись истории имён.
-- Выполняется Flyway один раз.

do $$
begin
    if to_regclass('log') is not null then
        insert into users (id, first_name, last_name, user_name, updated_at)
        select distinct on (user_id) user_id, user_first_name, user_last_name, user_name, now()
        from log
        where user_id is not null and user_id <> '' and user_id ~ '^[0-9]+$'
        order by user_id, id desc
        on conflict (id) do nothing;
    end if;
end
$$;

insert into user_name_history (user_id, first_name, last_name, user_name, changed_at)
select u.id, u.first_name, u.last_name, u.user_name, coalesce(u.updated_at, now())
from users u
where not exists (select 1 from user_name_history h where h.user_id = u.id);
