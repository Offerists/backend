--liquibase formatted sql

--changeset Artyom:1
create table app_users
(
    id bigint generated always as identity,
    telegram_id bigint not null,
    chat_id bigint not null,
    yougile_user_id varchar(255),
    yougile_api_key text,
    yougile_role varchar(50),
    yougile_company_id varchar(255),
    username varchar(255),
    full_name varchar(255),
    digest_enabled boolean not null default true,
    reminders_enabled boolean not null default true,
    created_at timestamptz not null default now(),

    constraint pk_app_users primary key (id),
    constraint uq_app_users_tg_id unique (telegram_id)
);
--rollback drop table app_users;

--changeset Artyom:2
create table tasks
(
    id bigint generated always as identity,
    yougile_task_id varchar(255) not null,
    telegram_id bigint not null,
    yougile_user_id varchar(255),
    title varchar(500) not null,
    status varchar(20) not null default 'TODO',
    deadline timestamptz,
    reminder_sent_at timestamptz,
    created_at timestamptz not null default now(),

    constraint pk_tasks primary key (id),
    constraint uq_tasks_yougile_id unique (yougile_task_id),
    constraint chk_tasks_status check (status in ('TODO', 'IN_PROGRESS', 'REVIEW', 'DONE'))
);
create index idx_tasks_telegram_id on tasks (telegram_id);
create index idx_tasks_deadline on tasks (deadline) where reminder_sent_at is null;
--rollback drop index idx_tasks_deadline; drop index idx_tasks_telegram_id; drop table tasks;

--changeset Artyom:3
create table message_history
(
    id bigint generated always as identity,
    chat_id bigint not null,
    telegram_user_id bigint,
    role varchar(20) not null,
    content text not null,
    created_at timestamptz not null default now(),

    constraint pk_message_history primary key (id),
    constraint chk_mh_role check (role in ('user', 'assistant', 'system'))
);
create index idx_message_history_chat_created on message_history (chat_id, created_at desc);
--rollback drop index idx_message_history_chat_created; drop table message_history;

--changeset Artyom:4 splitStatements:false
create or replace function trim_message_history() returns trigger language plpgsql as $$
begin
    delete from message_history
    where chat_id = new.chat_id
      and id not in (
          select id from message_history
          where chat_id = new.chat_id
          order by created_at desc
          limit 50
      );
    return null;
end;
$$;

create trigger trg_trim_message_history
    after insert on message_history
    for each row execute function trim_message_history();
--rollback drop trigger if exists trg_trim_message_history on message_history; drop function if exists trim_message_history();

--changeset Artyom:5
create table user_board_settings
(
    id bigint generated always as identity,
    telegram_id bigint not null,
    company_id varchar(255) not null,
    board_id varchar(255) not null,
    board_name varchar(255),
    column_todo_id varchar(255),
    column_in_progress_id varchar(255),
    column_review_id varchar(255),
    column_done_id varchar(255),
    is_default boolean not null default false,

    constraint pk_user_board_settings primary key (id),
    constraint uq_user_board_settings unique (telegram_id, board_id),
    constraint fk_ubs_user foreign key (telegram_id) references app_users (telegram_id)
);
create index idx_ubs_telegram_id on user_board_settings (telegram_id);
--rollback drop index idx_ubs_telegram_id; drop table user_board_settings;

--changeset Artyom:6
create table agent_context
(
    telegram_id bigint not null,
    last_task_id varchar(255),
    last_task_title varchar(500),
    last_assignee_id varchar(255),
    last_assignee_name varchar(255),
    last_status varchar(50),
    updated_at timestamptz not null default now(),

    constraint pk_agent_context primary key (telegram_id),
    constraint fk_ac_user foreign key (telegram_id) references app_users (telegram_id)
);
--rollback drop table agent_context;

--changeset Artyom:7
create table group_context
(
    chat_id bigint not null,
    summary text,
    total_messages int not null default 0,
    updated_at timestamptz not null default now(),

    constraint pk_group_context primary key (chat_id)
);
--rollback drop table group_context;

--changeset Artyom:8
create table scheduled_meetings
(
    id bigint generated always as identity,
    chat_id bigint not null,
    telegram_id bigint not null,
    url varchar(1024) not null,
    scheduled_at timestamptz not null,
    status varchar(20) not null default 'PENDING',
    created_at timestamptz not null default now(),

    constraint pk_scheduled_meetings primary key (id),
    constraint chk_sm_status check (status in ('PENDING', 'STARTED', 'DONE', 'FAILED'))
);
create index idx_sm_status_scheduled_at on scheduled_meetings (status, scheduled_at)
    where status = 'PENDING';
--rollback drop index idx_sm_status_scheduled_at; drop table scheduled_meetings;
