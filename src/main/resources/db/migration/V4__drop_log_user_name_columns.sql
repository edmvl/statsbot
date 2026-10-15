-- В log оставляем только user_id: ФИО/ник теперь берутся из users.
-- В PostgreSQL DROP COLUMN — операция уровня метаданных (быстрая, без перезаписи таблицы).

alter table log drop column if exists user_first_name;
alter table log drop column if exists user_last_name;
alter table log drop column if exists user_name;
