WITH data (username, date_created, is_deleted, text) AS (
    VALUES (
        'user_1',
        timestamp with time zone '2025-10-01T00:00:00Z',
        FALSE,
        'Howdy from user_1!'
    ), (
        'user_2',
        timestamp with time zone '2025-10-02T00:00:00Z',
        FALSE,
        'Howdy also from user_2!'
    ), (
        'user_1',
        timestamp with time zone '2025-10-03T00:00:00Z',
        TRUE,
        ''
    )
)
INSERT INTO public.posts_comment (
    id,
    date_created,
    is_deleted,
    text,
    author_id,
    post_id
)
SELECT
    uuidv4(),
    data.date_created,
    data.is_deleted,
    data.text,
    comment_author.id,
    (
        SELECT posts_post.id FROM public.posts_post
        INNER JOIN public.users_user ON author_id = users_user.id
        WHERE username = 'user_0'
        ORDER BY date_published
        LIMIT 1
    )
FROM data
INNER JOIN public.users_user comment_author ON data.username = comment_author.username;
