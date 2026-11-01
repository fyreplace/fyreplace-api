INSERT INTO public.users (
    username,
    password,
    email,
    active,
    rank,
    bio,
    ban_count,
    date_created,
    is_deleted
)
VALUES (
    'pending_user',
    '',
    'pending_user@example.org',
    FALSE,
    'CITIZEN',
    'I am pending',
    0,
    CURRENT_TIMESTAMP - interval '1 hour',
    FALSE
), (
    'passwordless_user',
    '',
    'passwordless_user@example.org',
    TRUE,
    'CITIZEN',
    'I have no password',
    0,
    timestamp with time zone '2026-07-02T00:00:00Z',
    FALSE
), (
    'password_user',
    '$2y$10$gIojBw072BX5FDDdN8SAm.q9K0F.4ajoMa3urVJAilDbb7DNbMQPy',
    'password_user@example.org',
    TRUE,
    'CITIZEN',
    'I have a password',
    0,
    timestamp with time zone '2026-07-03T00:00:00Z',
    FALSE
), (
    'deleted_user',
    '',
    'deleted_user@example.org',
    TRUE,
    'CITIZEN',
    'I am deleted',
    0,
    timestamp with time zone '2026-07-04T00:00:00Z',
    TRUE
);
