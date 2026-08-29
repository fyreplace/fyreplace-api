INSERT INTO public.posts_subscription (
    id,
    user_id,
    post_id,
    last_comment_seen_id,
    date_last_seen
)
VALUES (
    uuidv4(),
    (
        SELECT id FROM public.users_user
        WHERE username = 'user_1'
    ),
    (
        SELECT posts_post.id FROM public.posts_post
        INNER JOIN public.users_user ON author_id = users_user.id
        WHERE username = 'user_0'
        ORDER BY date_published
        LIMIT 1
    ),
    (
        SELECT posts_comment.id FROM public.posts_comment
        INNER JOIN public.posts_post ON post_id = posts_post.id
        ORDER BY posts_comment.date_created, posts_comment.id
        LIMIT 1
    ),
    timestamp with time zone '2025-10-01T12:00:00Z'
);
