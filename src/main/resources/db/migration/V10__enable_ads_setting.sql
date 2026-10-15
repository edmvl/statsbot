-- Настройка «Показывать рекламу» (enable_ads).
-- Реклама публикуется и принимаются предложения рекламы только в чатах,
-- где настройка включена. Чатам, где реклама уже настроена, включаем её,
-- чтобы ничего не перестало работать после обновления.

insert into chat_settings (chat_id, setting_name, setting_value)
select distinct a.chat_id, 'enable_ads', 'true'
from ads a
where a.chat_id is not null
  and not exists (
      select 1
      from chat_settings cs
      where cs.chat_id = a.chat_id
        and cs.setting_name = 'enable_ads'
  );
