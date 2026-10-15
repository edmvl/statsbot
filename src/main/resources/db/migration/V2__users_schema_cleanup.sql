-- Приведение схемы users к текущему виду (на случай, если таблица была создана ранее):
-- id как varchar (для джойна с log.user_id), колонка идентификатора файла аватара,
-- удаление временных birth_date/phone.

alter table users alter column id type varchar using id::varchar;
alter table users add column if not exists avatar_file_id varchar;
alter table users drop column if exists birth_date;
alter table users drop column if exists phone;
