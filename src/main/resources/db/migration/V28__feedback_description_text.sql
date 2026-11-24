-- Текст обращения не влезал в varchar(255): пользователь с длинным описанием
-- получал SQL error 22001 (value too long for type character varying(255)).
-- В feedback_chat.description уже text — приводим основную таблицу к тому же типу.

alter table feedback alter column description type text;
