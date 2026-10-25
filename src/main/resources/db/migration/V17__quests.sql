-- Квесты: чекпоинты с координатами и вопросами, участники и их прохождения.
create table if not exists quest (
    id bigserial primary key,
    title varchar(255) not null,
    description text,
    active boolean not null default true,
    created_by varchar(64),
    created_at timestamp not null default now()
);

create table if not exists quest_checkpoint (
    id bigserial primary key,
    quest_id bigint not null,
    sort_order int not null default 0,
    title varchar(255),
    question text not null,
    answer varchar(255) not null,
    lat double precision not null,
    lon double precision not null,
    radius_m int not null default 50,
    media_type varchar(16),
    media_file varchar(255),
    constraint fk_quest_checkpoint_quest foreign key (quest_id) references quest (id) on delete cascade
);

create index if not exists idx_quest_checkpoint_quest on quest_checkpoint (quest_id);

create table if not exists quest_participant (
    id bigserial primary key,
    quest_id bigint not null,
    user_id varchar(64) not null,
    started_at timestamp not null default now(),
    finished_at timestamp,
    constraint fk_quest_participant_quest foreign key (quest_id) references quest (id) on delete cascade,
    constraint uq_quest_participant unique (quest_id, user_id)
);

create index if not exists idx_quest_participant_quest on quest_participant (quest_id);

create table if not exists quest_pass (
    id bigserial primary key,
    participant_id bigint not null,
    checkpoint_id bigint not null,
    answer varchar(255),
    passed_at timestamp not null default now(),
    constraint fk_quest_pass_participant foreign key (participant_id)
        references quest_participant (id) on delete cascade,
    constraint fk_quest_pass_checkpoint foreign key (checkpoint_id)
        references quest_checkpoint (id) on delete cascade,
    constraint uq_quest_pass unique (participant_id, checkpoint_id)
);

create index if not exists idx_quest_pass_participant on quest_pass (participant_id);
