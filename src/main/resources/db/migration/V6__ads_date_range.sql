-- Период показа рекламы: с какого и по какое время пост показывается в чате.

alter table ads add column if not exists date_start timestamp;
alter table ads add column if not exists date_end timestamp;
