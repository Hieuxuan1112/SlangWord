create table app_user (
    id            bigserial primary key,
    username      varchar(64)  not null unique,
    password_hash varchar(255) not null,
    role          varchar(16)  not null,
    created_at    timestamptz  not null
);

create table slang_word (
    id         bigserial primary key,
    word       varchar(128) not null unique,
    created_at timestamptz  not null,
    updated_at timestamptz  not null
);
create index idx_slang_word_word_lower on slang_word (lower(word));

create table definition (
    id            bigserial primary key,
    slang_word_id bigint        not null references slang_word (id) on delete cascade,
    text          varchar(2000) not null
);
create index idx_definition_word on definition (slang_word_id);

create table search_history (
    id           bigserial primary key,
    user_id      bigint       not null references app_user (id) on delete cascade,
    keyword      varchar(128) not null,
    result_count integer      not null,
    searched_at  timestamptz  not null
);
create index idx_history_user on search_history (user_id, searched_at desc);

create table quiz_result (
    id             bigserial primary key,
    user_id        bigint       not null references app_user (id) on delete cascade,
    mode           varchar(32)  not null,
    prompt         varchar(512) not null,
    correct_answer varchar(512) not null,
    chosen_answer  varchar(512),
    is_correct     boolean      not null,
    answered_at    timestamptz  not null
);
create index idx_quiz_user on quiz_result (user_id, answered_at desc);
