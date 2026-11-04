-- Учёт приглашений в чат: кто кого добавил.
-- Одна строка на пару «пригласивший — приглашённый» в чате; повторное добавление
-- обновляет дату, но не плодит дубли.

create table if not exists chat_invite (
    id bigserial primary key,
    chat_id varchar(255) not null,
    inviter_id varchar(255) not null,
    invitee_id varchar(255) not null,
    invited_at timestamp not null default now()
);

create unique index if not exists chat_invite_chat_inviter_invitee_idx
    on chat_invite (chat_id, inviter_id, invitee_id);

create index if not exists chat_invite_chat_invitee_idx
    on chat_invite (chat_id, invitee_id);
