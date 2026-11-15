-- Таблица «кто сегодня проставляется»: один выбранный пользователь на чат и дату.
-- Фича была в коде с самого начала (Selected, SelectedRepo, SelectedService,
-- Job.sendSelected), но таблицу так и не создали, поэтому каждый день в 10:00
-- job падал с "relation selected does not exist".

create table if not exists selected (
    id      bigserial primary key,
    chat_id varchar not null,
    user_id varchar not null,
    date    date    not null
);

-- SelectedRepo.findByChatIdAndDate ожидает ровно одну запись на чат и дату.
create unique index if not exists selected_chat_id_date_uq
    on selected (chat_id, date);
