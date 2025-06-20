-- SEQUENCE для пользователей и токенов
CREATE SEQUENCE seq_users_id
    START WITH 1 INCREMENT BY 50;

CREATE SEQUENCE seq_password_reset_tokens_id
    START WITH 1 INCREMENT BY 50;

-- Создание таблицы пользователей
create table users (
   id         BIGINT PRIMARY KEY DEFAULT nextval('seq_users_id'),
   email     varchar(100) not null constraint users_email_unique unique,
   password  varchar(255) not null,
   username  varchar(50)  not null constraint users_username_unique unique,
   guest      BOOLEAN DEFAULT FALSE,
   created_at timestamp not null default current_timestamp,
   updated_at timestamp not null default current_timestamp
);

-- Индексы (по желанию, если часто ищешь по email/username)
create index idx_users_username on users (username);
create index idx_users_email on users (email);


-- Таблица ролей пользователей (User -> Roles)
create table user_roles (
   user_id bigint not null,
   role    varchar(255) not null,
   constraint fk_user_roles_user_id foreign key (user_id) references users(id) on delete cascade
);

-- Таблица для хранения токенов восстановления пароля
create table password_reset_tokens (
   id           BIGINT PRIMARY KEY DEFAULT nextval('seq_password_reset_tokens_id'),
   expiry_date  timestamp(6) not null,
   token        varchar(255) not null constraint password_reset_token_unique unique,
   user_id      bigint not null constraint password_reset_user_unique unique,
   constraint fk_password_reset_user foreign key (user_id) references users(id) on delete cascade
);

create index idx_token on password_reset_tokens (token);