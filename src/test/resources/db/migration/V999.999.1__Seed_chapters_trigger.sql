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
    'trigger_author',
    '!',
    'trigger_author@example.org',
    TRUE,
    'CITIZEN',
    'Trigger test author',
    0,
    timestamp with time zone '2026-08-01T00:00:00Z',
    FALSE
);

WITH author AS (
    SELECT id FROM public.users
    WHERE username = 'trigger_author'
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
    timestamp with time zone '2026-08-02T00:00:00Z',
    FALSE,
    FALSE,
    7,
    (SELECT id FROM author),
    TRUE
), (
    timestamp with time zone '2026-08-03T00:00:00Z',
    FALSE,
    FALSE,
    0,
    (SELECT id FROM author),
    FALSE
);

WITH post AS (
    SELECT posts.id FROM public.posts
    INNER JOIN public.users ON posts.author_id = users.id
    WHERE users.username = 'trigger_author' AND posts.is_published
)
INSERT INTO public.comments (
    date_created,
    is_deleted,
    anonymous,
    text,
    author_id,
    post_id
)
VALUES (
    timestamp with time zone '2026-08-04T00:00:00Z',
    FALSE,
    FALSE,
    'First comment',
    (SELECT id FROM public.users WHERE username = 'user_1'),
    (SELECT id FROM post)
),
(
    timestamp with time zone '2026-08-05T00:00:00Z',
    FALSE,
    FALSE,
    'Second comment',
    (SELECT id FROM public.users WHERE username = 'user_2'),
    (SELECT id FROM post)
),
(
    timestamp with time zone '2026-08-06T00:00:00Z',
    TRUE,
    FALSE,
    'Deleted comment',
    (SELECT id FROM public.users WHERE username = 'user_1'),
    (SELECT id FROM post)
);
