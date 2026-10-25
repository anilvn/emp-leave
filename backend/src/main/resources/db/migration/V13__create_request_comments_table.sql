create table request_comments (
    id         bigint generated always as identity primary key,
    request_id bigint      not null references leave_requests (id),
    author_id  bigint      not null references users (id),
    body       text        not null,
    created_at timestamptz not null default now()
);
