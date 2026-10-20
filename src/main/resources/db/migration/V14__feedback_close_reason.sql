-- Закрытие жалобы с причиной отказа.
alter table feedback add column if not exists close_reason text;
alter table feedback add column if not exists closed_at timestamp;
