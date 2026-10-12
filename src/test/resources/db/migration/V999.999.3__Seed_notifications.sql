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
    'deleted_post_viewer',
    '!',
    'deleted_post_viewer@example.org',
    TRUE,
    'CITIZEN',
    '',
    0,
    timestamp with time zone '2026-08-12T00:00:00Z',
    FALSE
);

WITH author AS (
    SELECT id FROM public.users
    WHERE username = 'deleted_post_viewer'
)
INSERT INTO public.posts (
    date_created,
    is_deleted,
    anonymous,
    life,
    author_id,
    is_published
)
VALUES (
    timestamp with time zone '2026-08-12T01:00:00Z',
    TRUE,
    FALSE,
    0,
    (SELECT id FROM author),
    TRUE
);

WITH viewer AS (
    SELECT id FROM public.users
    WHERE username = 'deleted_post_viewer'
),
post AS (
    SELECT posts.id FROM public.posts
    INNER JOIN public.users ON posts.author_id = users.id
    WHERE users.username = 'deleted_post_viewer'
)
INSERT INTO public.subscriptions (
    user_id,
    post_id,
    last_comment_seen_id,
    date_last_seen
)
VALUES (
    (SELECT id FROM viewer),
    (SELECT id FROM post),
    NULL,
    timestamp with time zone '2026-08-12T02:00:00Z'
);
