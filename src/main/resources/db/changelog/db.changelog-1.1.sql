--liquibase formatted sql

--changeset Artyom:1
create table workspace_settings
(
    chat_id bigint not null,
    yougile_api_key varchar(100),
    column_todo_id varchar(50),
    column_in_progress_id varchar(50),
    column_review_id varchar(50),
    column_done_id varchar(50),
    is_configured boolean default false not null,
    created_at timestamp default now() not null,

    constraint pk_workspace_settings primary key (chat_id)
);

--rollback DROP TABLE workspace_settings;

--changeset Artyom:2
create table app_users
(
    id bigint generated always as identity,
    telegram_id bigint not null,
    chat_id bigint not null,
    yougile_user_id varchar(50),
    username varchar(255),
    full_name varchar(255),
    created_at timestamp default now() not null,

    constraint pk_app_users primary key (id),
    constraint uq_app_users_telegram unique (telegram_id, chat_id),
    constraint fk_app_users_workspace foreign key (chat_id) references workspace_settings (chat_id)
);

--rollback DROP TABLE app_users;

--changeset Artyom:3
create table tasks
(
    id bigint generated always as identity,
    yougile_task_id varchar(50) not null,
    chat_id bigint not null,
    assignee_id bigint,
    title varchar(500) not null,
    status varchar(20) default 'TODO' not null,
    deadline timestamp,
    reminder_sent_at timestamp,
    created_at timestamp default now() not null,

    constraint pk_tasks primary key (id),
    constraint uq_tasks_yougile_id unique (yougile_task_id),
    constraint fk_tasks_workspace foreign key (chat_id) references workspace_settings (chat_id),
    constraint fk_tasks_assignee foreign key (assignee_id) references app_users (id)
);

--rollback drop table tasks;

--changeset Artyom:4
create table message_history
(
    id bigint generated always as identity,
    chat_id bigint not null,
    telegram_user_id bigint,
    role varchar(20) not null,
    content text not null,
    created_at timestamp default now() not null,

    constraint pk_message_history primary key (id),
    constraint fk_message_history_workspace foreign key (chat_id) references workspace_settings (chat_id)
);

create index idx_message_history_chat_created on message_history (chat_id, created_at desc);

--rollback drop index idx_message_history_chat_created; drop table message_history;
