-- Ускоряет выборку участников/администраторов чата: distinct user_id по chat_id.

create index if not exists idx_log_chat_id_user_id on log (chat_id, user_id);
