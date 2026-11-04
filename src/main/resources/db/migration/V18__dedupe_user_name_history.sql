-- Чистим дубли в истории смены ФИО/ника.
--
-- Историю восстанавливали из дампа и писали приложением: у части пользователей
-- накопились подряд идущие одинаковые записи, а также «качания» вида
--   first_name='Иван Петров'  <->  first_name='Иван', last_name='Петров',
-- которые по факту один и тот же пользователь. Оставляем только переходы между
-- разными отображаемыми именами (или никами) — по одному на смену.

with norm as (
    select id,
           user_id,
           btrim(concat_ws(' ', nullif(btrim(first_name), ''), nullif(btrim(last_name), ''))) as display_name,
           btrim(user_name) as user_name,
           changed_at
    from user_name_history
),
ordered as (
    select id,
           user_id,
           display_name,
           user_name,
           lag(display_name) over w as prev_display,
           lag(user_name)    over w as prev_user
    from norm
    window w as (partition by user_id order by changed_at, id)
)
delete from user_name_history h
where h.id in (
    select id
    from ordered
    where display_name is not distinct from prev_display
      and user_name    is not distinct from prev_user
);
