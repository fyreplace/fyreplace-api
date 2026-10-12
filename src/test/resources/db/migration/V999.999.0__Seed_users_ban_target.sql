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
    'ban_target',
    '!',
    'ban_target@example.org',
    TRUE,
    'CITIZEN',
    'Ban target',
    0,
    timestamp with time zone '2026-08-01T00:00:00Z',
    FALSE
);
