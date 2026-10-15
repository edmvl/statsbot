-- Предложения рекламы от пользователей и оплата звёздами.
--
-- status: ACTIVE   — опубликован (создан админом или оплачен), работает как обычная реклама;
--         PENDING  — предложение пользователя, ждёт решения супер-админа;
--         APPROVED — одобрено супер-админом, ждёт оплаты звёздами;
--         REJECTED — отклонено.

alter table ads add column if not exists status varchar(20) not null default 'ACTIVE';
alter table ads add column if not exists user_id varchar(255);
alter table ads add column if not exists days integer;
alter table ads add column if not exists stars_paid integer;
alter table ads add column if not exists payment_charge_id varchar(255);
alter table ads add column if not exists created_at timestamp not null default now();

create index if not exists idx_ads_status on ads (status);
create index if not exists idx_ads_user_id on ads (user_id);
