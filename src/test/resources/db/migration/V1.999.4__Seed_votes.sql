WITH data (username, post_author, spread) AS (
    VALUES ('user_0', 'user_1', TRUE),
        ('user_1', 'user_0', FALSE)
)
INSERT INTO public.posts_vote (id, user_id, post_id, spread)
SELECT
    uuidv4(),
    users_user.id,
    (
        SELECT posts_post.id FROM public.posts_post
        INNER JOIN public.users_user ON author_id = users_user.id
        WHERE username = data.post_author
        ORDER BY date_published
        LIMIT 1
    ),
    data.spread
FROM data
INNER JOIN public.users_user ON data.username = users_user.username;
