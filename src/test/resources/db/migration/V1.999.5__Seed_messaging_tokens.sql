INSERT INTO public.users_connection (
    id,
    user_id,
    date_created,
    date_last_used,
    hardware,
    software
)
SELECT
    uuidv4(),
    users_user.id,
    users_user.date_joined,
    CURRENT_TIMESTAMP,
    'unknown',
    'unknown'
FROM users_user;

WITH data (username, service, token) AS (
    VALUES
        ('user_0', 1, 'apns-token-user-0'),
        ('user_1', 2, 'fcm-token-user-1')
)
INSERT INTO public.notifications_remotemessaging (
    id,
    connection_id,
    service,
    token
)
SELECT
    uuidv4(),
    users_connection.id,
    data.service,
    data.token
FROM data
INNER JOIN public.users_user ON data.username = users_user.username
INNER JOIN public.users_connection ON users_user.id = users_connection.user_id;
