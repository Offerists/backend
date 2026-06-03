--liquibase formatted sql

--changeset Artyom:5
alter table app_users
    add column yougile_api_key varchar(255),
    add column yougile_role varchar(50),
    add column yougile_company_id varchar(255);

alter table app_users
    add constraint uq_app_users_telegram_id unique (telegram_id);

--rollback alter table app_users drop constraint uq_app_users_telegram_id; alter table app_users drop column yougile_api_key, drop column yougile_role, drop column yougile_company_id;

--changeset Artyom:6
create table user_board_settings
(
    id bigint generated always as identity,
    telegram_id bigint not null,
    company_id varchar(255) not null,
    board_id varchar(255) not null,
    column_todo_id varchar(255),
    column_in_progress_id varchar(255),
    column_review_id varchar(255),
    column_done_id varchar(255),
    is_default boolean default false not null,

    constraint pk_user_board_settings primary key (id),
    constraint uq_user_board_settings unique (telegram_id, board_id),
    constraint fk_user_board_settings_user foreign key (telegram_id)
        references app_users (telegram_id)
);

--rollback drop table user_board_settings;

--changeset Artyom:7
alter table app_users       drop constraint if exists fk_app_users_workspace;
alter table message_history drop constraint if exists fk_message_history_workspace;
alter table tasks            drop constraint if exists fk_tasks_workspace;
alter table tasks            drop constraint if exists fk_tasks_assignee;

--rollback select 1;
