INSERT INTO public.posts_post (
    id,
    date_created,
    date_published,
    author_id,
    life,
    is_anonymous,
    is_deleted
)
VALUES (
    uuidv4(),
    timestamp with time zone '2025-01-09T00:00:00Z',
    timestamp with time zone '2025-01-12T00:00:00Z',
    (SELECT id FROM public.users_user WHERE username = 'user_0'),
    4,
    FALSE,
    FALSE
), (
    uuidv4(),
    timestamp with time zone '2025-06-17T00:00:00Z',
    timestamp with time zone '2025-06-25T00:00:00Z',
    (SELECT id FROM public.users_user WHERE username = 'user_0'),
    3,
    TRUE,
    FALSE
), (
    uuidv4(),
    timestamp with time zone '2025-03-01T00:00:00Z',
    timestamp with time zone '2025-03-03T00:00:00Z',
    (SELECT id FROM public.users_user WHERE username = 'user_1'),
    5,
    FALSE,
    TRUE
), (
    uuidv4(),
    timestamp with time zone '2025-04-12T00:00:00Z',
    NULL,
    (SELECT id FROM public.users_user WHERE username = 'user_1'),
    0,
    FALSE,
    FALSE
);

WITH data (post_author, position, text, is_title, image) AS (
    VALUES (
        'user_0',
        0,
        '1st chapter of the 1st post from user_0',
        TRUE,
        NULL
    ), (
        'user_0',
        1,
        '2nd chapter of the 1st post from user_0',
        FALSE,
        NULL
    ), (
        'user_1',
        0,
        '',
        FALSE,
        'chapters/some_pic.jpeg'
    )
)
INSERT INTO public.posts_chapter (
    id,
    post_id,
    position,
    text,
    is_title,
    image,
    width,
    height
)
SELECT
    uuidv4(),
    (
        SELECT posts_post.id FROM public.posts_post
        INNER JOIN public.users_user ON author_id = users_user.id
        WHERE username = data.post_author
        ORDER BY date_published
        LIMIT 1
    ),
    data.position,
    data.text,
    data.is_title,
    data.image,
    (CASE data.image IS NULL WHEN TRUE THEN 0 ELSE 1280 END),
    (CASE data.image IS NULL WHEN TRUE THEN 0 ELSE 720 END)
FROM data;
