-- Правки сообщений: в log храним последнюю версию, предыдущие — в log_edit.
-- message_id нужен, чтобы найти строку по сообщению Telegram при правке/удалении.
alter table log add column if not exists message_id int;
alter table log add column if not exists edited_at timestamp;
alter table log add column if not exists deleted boolean not null default false;

create index if not exists log_chat_message_idx on log (chat_id, message_id);

create table if not exists log_edit (
    id bigserial primary key,
    log_id bigint not null,
    text text,
    filetype varchar(64),
    file varchar(255),
    edited_at timestamp not null
);

create index if not exists log_edit_log_idx on log_edit (log_id);
