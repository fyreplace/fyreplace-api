INSERT INTO public.django_content_type (app_label, model)
VALUES ('users', 'user'), ('posts', 'post'), ('posts', 'comment');

WITH data (issuer_username, app_label, model, target_id) AS (
    VALUES (
        'user_2',
        'users',
        'user',
        (
            SELECT id FROM public.users_user
            WHERE username = 'user_0'
        )
    ), (
        'user_1',
        'posts',
        'post',
        (
            SELECT posts_post.id FROM public.posts_post
            INNER JOIN public.users_user ON author_id = users_user.id
            WHERE username = 'user_0'
            ORDER BY date_published DESC
            LIMIT 1
        )
    ), (
        'user_0',
        'posts',
        'comment',
        (
            SELECT posts_comment.id FROM public.posts_comment
            INNER JOIN public.users_user ON author_id = users_user.id
            WHERE username = 'user_1'
            ORDER BY date_created
            LIMIT 1
        )
    )
)
INSERT INTO public.notifications_flag (id, issuer_id, target_id, target_type_id)
SELECT uuidv4(), issuer.id, data.target_id, django_content_type.id
FROM data
INNER JOIN public.users_user issuer ON data.issuer_username = issuer.username
INNER JOIN public.django_content_type
    ON data.app_label = django_content_type.app_label
    AND data.model = django_content_type.model;
