-- Таблицы пользователей: текущие данные и история смены имени.

create table if not exists users (
    id varchar primary key,
    first_name varchar,
    last_name varchar,
    user_name varchar,
    avatar_path varchar,
    avatar_file_id varchar,
    updated_at timestamp
);

create table if not exists user_name_history (
    id bigserial primary key,
    user_id varchar,
    first_name varchar,
    last_name varchar,
    user_name varchar,
    changed_at timestamp
);
