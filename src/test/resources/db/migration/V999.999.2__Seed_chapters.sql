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
    'chapter_author',
    '!',
    'chapter_author@example.org',
    TRUE,
    'CITIZEN',
    'Chapter move test author',
    0,
    timestamp with time zone '2026-08-10T00:00:00Z',
    FALSE
);

WITH author AS (
    SELECT id FROM public.users
    WHERE username = 'chapter_author'
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
    timestamp with time zone '2026-08-11T00:00:00Z',
    FALSE,
    FALSE,
    0,
    (SELECT id FROM author),
    TRUE
);

WITH post AS (
    SELECT posts.id FROM public.posts
    INNER JOIN public.users ON posts.author_id = users.id
    WHERE users.username = 'chapter_author'
)
INSERT INTO public.chapters (
    post_id,
    position,
    text,
    width,
    height
)
VALUES
    ((SELECT id FROM post), 0, 'chapter_0', 10, 1),
    ((SELECT id FROM post), 1, 'chapter_1', 20, 1),
    ((SELECT id FROM post), 2, 'chapter_2', 30, 1),
    ((SELECT id FROM post), 3, 'chapter_3', 40, 1);
