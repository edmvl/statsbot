-- Наборы стикеров, которые создаёт и пополняет бот (команда «.стикер»).
-- Бот может редактировать только созданные им наборы, поэтому имя оканчивается
-- на «_by_<username бота>». Когда набор упирается в лимит Telegram (120 статиков),
-- создаётся следующий: hamoryal_2_by_..., hamoryal_3_by_... и т.д.
create table if not exists sticker_pack (
    name           varchar(64) primary key,
    title          varchar(64) not null,
    stickers_count integer     not null default 0,
    created_at     timestamp   not null default now()
);
