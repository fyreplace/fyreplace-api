-----------------------------
-- Create local procedures --
-----------------------------

CREATE PROCEDURE constraint__rename(table_name varchar, name_pattern varchar, new_name varchar)
LANGUAGE plpgsql
AS
$$
DECLARE
    old_name varchar;
BEGIN
    SELECT conname
    INTO old_name
    FROM pg_constraint
    INNER JOIN pg_class ON conrelid = pg_class.oid
    INNER JOIN pg_namespace ON relnamespace = pg_namespace.oid
    WHERE nspname = 'public'
        AND relname = table_name
        AND conname LIKE name_pattern;

    EXECUTE format(
        'ALTER TABLE public.%I RENAME CONSTRAINT %I TO %I',
        table_name,
        old_name,
        new_name
    );
END;
$$;

CREATE PROCEDURE index__rename(name_pattern varchar, new_name varchar)
LANGUAGE plpgsql
AS
$$
DECLARE
    old_name varchar;
BEGIN
    SELECT relname
    INTO old_name
    FROM pg_class
    INNER JOIN pg_namespace ON relnamespace = pg_namespace.oid
    WHERE nspname = 'public'
        AND relkind = 'i'
        AND relname LIKE name_pattern;

    EXECUTE format(
        'ALTER INDEX public.%I RENAME TO %I',
        old_name,
        new_name
    );
END;
$$;

---------------------------
-- Flush deferred checks --
---------------------------

SET CONSTRAINTS ALL IMMEDIATE;

------------------
-- Stored files --
------------------

CREATE TABLE public.stored_files (
    id uuid NOT NULL PRIMARY KEY DEFAULT uuidv7(),
    path character varying(255) NOT NULL UNIQUE
);

INSERT INTO public.stored_files (path)
SELECT DISTINCT avatar FROM public.users_user
WHERE avatar IS NOT NULL
ON CONFLICT (path) DO NOTHING;

INSERT INTO public.stored_files (path)
SELECT DISTINCT image FROM public.posts_chapter
WHERE image IS NOT NULL
ON CONFLICT (path) DO NOTHING;

-----------
-- Users --
-----------

-- Drop old columns

ALTER TABLE public.users_user
DROP COLUMN last_login;

ALTER TABLE public.users_user
DROP COLUMN first_name;

ALTER TABLE public.users_user
DROP COLUMN last_name;

ALTER TABLE public.users_user
DROP COLUMN connection_token;

-- Rename columns

ALTER TABLE public.users_user
RENAME COLUMN date_joined TO date_created;

ALTER TABLE public.users_user
RENAME COLUMN is_active TO active;

-- Replace column avatar to reference stored_files

ALTER TABLE public.users_user
ADD COLUMN avatar_id uuid
CONSTRAINT users__avatar_id__fk REFERENCES public.stored_files(id) ON DELETE SET NULL;

UPDATE public.users_user
SET avatar_id = (
    SELECT id FROM public.stored_files
    WHERE path = avatar
)
WHERE avatar IS NOT NULL;

ALTER TABLE public.users_user
DROP COLUMN avatar;

CREATE INDEX users__avatar_id__idx ON public.users_user (avatar_id);

-- Replace column is_banned with ban_count

ALTER TABLE public.users_user
ADD COLUMN ban_count integer NOT NULL DEFAULT 0;

UPDATE public.users_user
SET ban_count = 1
WHERE is_banned AND date_ban_end IS NOT NULL;

UPDATE public.users_user
SET ban_count = 2
WHERE is_banned AND date_ban_end IS NULL;

ALTER TABLE public.users_user
DROP COLUMN is_banned;

-- Add column rank

CREATE TYPE public.user_rank AS ENUM(
    'CITIZEN',
    'STAFF',
    'ADMIN'
);

ALTER TABLE public.users_user
ADD COLUMN rank public.user_rank NOT NULL DEFAULT 'CITIZEN';

UPDATE public.users_user
SET rank = (
    CASE
        WHEN is_superuser THEN 'ADMIN'::user_rank
        WHEN is_staff THEN 'STAFF'::user_rank
    END
)
WHERE is_superuser OR is_staff;

ALTER TABLE public.users_user
DROP COLUMN is_staff;

ALTER TABLE public.users_user
DROP COLUMN is_superuser;

-- Rename constraints and indexes

CALL index__rename(
    'users_user_username_%_like',
    'users__username__idx_like'
);

CALL index__rename(
    'users_user_email_%_like',
    'users__email__idx_like'
);

ALTER TABLE public.users_user
RENAME CONSTRAINT users_user_username_key TO users__username__uniq;

ALTER TABLE public.users_user
RENAME CONSTRAINT users_user_email_key TO users__email__uniq;

------------
-- Blocks --
------------

-- Rename constraints and indexes

CALL constraint__rename(
    'users_block',
    'users_block_issuer_id_target_id_%_uniq',
    'blocks__issuer_id__target_id__uniq'
);

CALL constraint__rename(
    'users_block',
    'users_block_issuer_id_%_fk_users_user_id',
    'blocks__issuer_id__fk'
);

CALL constraint__rename(
    'users_block',
    'users_block_target_id_%_fk_users_user_id',
    'blocks__target_id__fk'
);

CALL index__rename(
    'users_block_issuer_id_%',
    'blocks__issuer_id__idx'
);

CALL index__rename(
    'users_block_target_id_%',
    'blocks__target_id__idx'
);

ALTER TABLE public.users_block
RENAME CONSTRAINT issuer_ne_target TO blocks__issuer__target__ne;

ALTER TABLE public.users_block
DROP CONSTRAINT blocks__issuer_id__fk;

ALTER TABLE public.users_block
ADD CONSTRAINT blocks__issuer_id__fk FOREIGN KEY (issuer_id) REFERENCES public.users_user(id) ON DELETE CASCADE;

ALTER TABLE public.users_block
DROP CONSTRAINT blocks__target_id__fk;

ALTER TABLE public.users_block
ADD CONSTRAINT blocks__target_id__fk FOREIGN KEY (target_id) REFERENCES public.users_user(id) ON DELETE CASCADE;

-----------
-- Posts --
-----------

-- Rename columns

ALTER TABLE public.posts_post
RENAME COLUMN is_anonymous TO anonymous;

-- Replace column date_published with is_published

ALTER TABLE public.posts_post
ADD COLUMN is_published boolean NOT NULL DEFAULT FALSE;

UPDATE public.posts_post
SET is_published = TRUE
WHERE date_published IS NOT NULL;

UPDATE public.posts_post
SET date_created = date_published
WHERE date_published IS NOT NULL;

ALTER TABLE public.posts_post
DROP COLUMN date_published;

-- Rename constraints and indexes

CALL constraint__rename(
    'posts_post',
    'posts_post_author_id_%_fk_users_user_id',
    'posts__author_id__fk'
);

CALL index__rename(
    'posts_post_author_id_%',
    'posts__author_id__idx'
);

ALTER TABLE public.posts_post
DROP CONSTRAINT posts__author_id__fk;

ALTER TABLE public.posts_post
ADD CONSTRAINT posts__author_id__fk FOREIGN KEY (author_id) REFERENCES public.users_user(id) ON DELETE CASCADE;

--------------
-- Chapters --
--------------

-- Replace column image to reference stored_files

ALTER TABLE public.posts_chapter
ADD COLUMN image_id uuid
CONSTRAINT chapters__image_id__fk REFERENCES public.stored_files(id) ON DELETE SET NULL;

UPDATE public.posts_chapter
SET image_id = stored_files.id
FROM public.stored_files
WHERE image = path;

ALTER TABLE public.posts_chapter
DROP COLUMN image;

CREATE INDEX chapters__image_id__idx ON public.posts_chapter (image_id);

-- Replace column position to use integers

ALTER TABLE public.posts_chapter
ADD COLUMN position_int integer;

UPDATE public.posts_chapter AS current
SET
    position_int = (
        SELECT count(*) FROM public.posts_chapter AS others
        WHERE current.post_id = others.post_id
            AND current.position > others.position
    )
FROM public.posts_post
WHERE current.post_id = posts_post.id;

ALTER TABLE public.posts_chapter
ALTER COLUMN position_int SET NOT NULL;

ALTER TABLE public.posts_chapter
DROP COLUMN position;

ALTER TABLE public.posts_chapter
RENAME COLUMN position_int TO position;

ALTER TABLE public.posts_chapter
ADD CONSTRAINT chapters__post_id__position__uniq UNIQUE (
    post_id,
    position
) DEFERRABLE INITIALLY DEFERRED;

-- Drop old columns

ALTER TABLE public.posts_chapter
DROP COLUMN is_title;

-- constraints and indexes

CALL constraint__rename(
    'posts_chapter',
    'posts_chapter_post_id_%_fk_posts_post_id',
    'chapters__post_id__fk'
);

CALL index__rename(
    'posts_chapter_post_id_%',
    'chapters__post_id__idx'
);

ALTER TABLE public.posts_chapter
DROP CONSTRAINT chapters__post_id__fk;

ALTER TABLE public.posts_chapter
ADD CONSTRAINT chapters__post_id__fk FOREIGN KEY (post_id) REFERENCES public.posts_post(id) ON DELETE CASCADE;

-------------------
-- Subscriptions --
-------------------

-- Rename constraints and indexes

CALL constraint__rename(
    'posts_subscription',
    'posts_subscription_user_id_post_id_%_uniq',
    'subscriptions__user_id__post_id__uniq'
);

CALL constraint__rename(
    'posts_subscription',
    'posts_subscription_user_id_%_fk_users_user_id',
    'subscriptions__user_id__fk'
);

CALL constraint__rename(
    'posts_subscription',
    'posts_subscription_post_id_%_fk_posts_post_id',
    'subscriptions__post_id__fk'
);

CALL constraint__rename(
    'posts_subscription',
    'posts_subscription_last_comment_seen_id_%_fk_posts_com',
    'subscriptions__last_comment_seen_id__fk'
);

CALL index__rename(
    'posts_subscription_user_id_%',
    'subscriptions__user_id__idx'
);

CALL index__rename(
    'posts_subscription_post_id_%',
    'subscriptions__post_id__idx'
);

CALL index__rename(
    'posts_subscription_last_comment_seen_id_%',
    'subscriptions__last_comment_seen_id__idx'
);

ALTER TABLE public.posts_subscription
DROP CONSTRAINT subscriptions__user_id__fk;

ALTER TABLE public.posts_subscription
ADD CONSTRAINT subscriptions__user_id__fk FOREIGN KEY (user_id) REFERENCES public.users_user(id) ON DELETE CASCADE;

ALTER TABLE public.posts_subscription
DROP CONSTRAINT subscriptions__post_id__fk;

ALTER TABLE public.posts_subscription
ADD CONSTRAINT subscriptions__post_id__fk FOREIGN KEY (post_id) REFERENCES public.posts_post(id) ON DELETE CASCADE;

ALTER TABLE public.posts_subscription
DROP CONSTRAINT subscriptions__last_comment_seen_id__fk;

ALTER TABLE public.posts_subscription
ADD CONSTRAINT subscriptions__last_comment_seen_id__fk FOREIGN KEY (last_comment_seen_id) REFERENCES public.posts_comment(id) ON DELETE CASCADE;

--------------
-- Comments --
--------------

-- Add column anonymous

ALTER TABLE public.posts_comment
ADD COLUMN anonymous boolean NOT NULL DEFAULT FALSE;

-- Rename indexes and constraints

CALL constraint__rename(
    'posts_comment',
    'posts_comment_author_id_%_fk_users_user_id',
    'comments__author_id__fk'
);

CALL constraint__rename(
    'posts_comment',
    'posts_comment_post_id_%_fk_posts_post_id',
    'comments__post_id__fk'
);

CALL index__rename(
    'posts_comment_author_id_%',
    'comments__author_id__idx'
);

CALL index__rename(
    'posts_comment_post_id_%',
    'comments__post_id__idx'
);

ALTER TABLE public.posts_comment
DROP CONSTRAINT comments__author_id__fk;

ALTER TABLE public.posts_comment
ADD CONSTRAINT comments__author_id__fk FOREIGN KEY (author_id) REFERENCES public.users_user(id) ON DELETE CASCADE;

ALTER TABLE public.posts_comment
DROP CONSTRAINT comments__post_id__fk;

ALTER TABLE public.posts_comment
ADD CONSTRAINT comments__post_id__fk FOREIGN KEY (post_id) REFERENCES public.posts_post(id) ON DELETE CASCADE;

CREATE INDEX comments__date_created__id__idx ON posts_comment (date_created, id);

CREATE INDEX comments__post_id__date_created__id__idx ON posts_comment (post_id, date_created, id);

-----------
-- Votes --
-----------

-- Rename constraints and indexes

CALL constraint__rename(
    'posts_vote',
    'posts_vote_user_id_post_id_%_uniq',
    'votes__user_id__post_id__uniq'
);

CALL constraint__rename(
    'posts_vote',
    'posts_vote_post_id_%_fk_posts_post_id',
    'votes__post_id__fk'
);

CALL constraint__rename(
    'posts_vote',
    'posts_vote_user_id_%_fk_users_user_id',
    'votes__user_id__fk'
);

CALL index__rename(
    'posts_vote_user_id_%',
    'votes__user_id__idx'
);

CALL index__rename(
    'posts_vote_post_id_%',
    'votes__post_id__idx'
);

ALTER TABLE public.posts_vote
DROP CONSTRAINT votes__user_id__fk;

ALTER TABLE public.posts_vote
ADD CONSTRAINT votes__user_id__fk FOREIGN KEY (user_id) REFERENCES public.users_user(id) ON DELETE CASCADE;

ALTER TABLE public.posts_vote
DROP CONSTRAINT votes__post_id__fk;

ALTER TABLE public.posts_vote
ADD CONSTRAINT votes__post_id__fk FOREIGN KEY (post_id) REFERENCES public.posts_post(id) ON DELETE CASCADE;

----------------------
-- Remote messaging --
----------------------

-- Replace column service to use an enum

CREATE TYPE public.remote_service AS ENUM (
    'APNS',
    'FCM',
    'WEB',
    'WNS'
);

ALTER TABLE public.notifications_remotemessaging
RENAME COLUMN service TO service_int;

ALTER TABLE public.notifications_remotemessaging
ADD COLUMN service public.remote_service;

UPDATE public.notifications_remotemessaging
SET service = (
    CASE
        WHEN service_int = 1 THEN 'APNS'::public.remote_service
        WHEN service_int = 2 THEN 'FCM'::public.remote_service
    END
)
WHERE TRUE;

ALTER TABLE public.notifications_remotemessaging
ALTER COLUMN service SET NOT NULL;

ALTER TABLE public.notifications_remotemessaging
DROP COLUMN service_int;

-- Replace column connection_id with user_id

ALTER TABLE public.notifications_remotemessaging
ADD COLUMN user_id uuid
CONSTRAINT messaging_tokens__user_id__fk REFERENCES public.users_user(id) ON DELETE CASCADE;

UPDATE public.notifications_remotemessaging
SET user_id = (
    SELECT users_connection.user_id FROM public.users_connection
    WHERE connection_id = users_connection.id
)
WHERE TRUE;

ALTER TABLE public.notifications_remotemessaging
ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE public.notifications_remotemessaging
DROP COLUMN connection_id;

CREATE INDEX messaging_tokens__user_id__idx ON public.notifications_remotemessaging (user_id);

-- Rename constraints and indexes

CALL index__rename(
    'notifications_remotemessaging_token_%_like',
    'messaging_tokens__token__idx_like'
);

ALTER TABLE public.notifications_remotemessaging
RENAME CONSTRAINT notifications_remotemessaging_token_key TO messaging_tokens__token__uniq;

-------------
-- Reports --
-------------

-- Replace columns target_type_id, target_id with target_*_id

ALTER TABLE public.notifications_flag
ADD COLUMN target_user_id uuid
CONSTRAINT reports__target_user_id__fk REFERENCES public.users_user(id) ON DELETE CASCADE;

ALTER TABLE public.notifications_flag
ADD COLUMN target_post_id uuid
CONSTRAINT reports__target_post_id__fk REFERENCES public.posts_post(id) ON DELETE CASCADE;

ALTER TABLE public.notifications_flag
ADD COLUMN target_comment_id uuid
CONSTRAINT reports__target_comment_id__fk REFERENCES public.posts_comment(id) ON DELETE CASCADE;

UPDATE public.notifications_flag
SET target_user_id = target_id::uuid
WHERE (
    SELECT TRUE FROM public.django_content_type
    WHERE django_content_type.id = target_type_id
        AND django_content_type.app_label = 'users'
        AND lower(django_content_type.model) = 'user'
);

UPDATE public.notifications_flag
SET target_post_id = target_id::uuid
WHERE (
    SELECT TRUE FROM public.django_content_type
    WHERE django_content_type.id = target_type_id
        AND django_content_type.app_label = 'posts'
        AND lower(django_content_type.model) = 'post'
);

UPDATE public.notifications_flag
SET target_comment_id = target_id::uuid
WHERE (
    SELECT TRUE FROM public.django_content_type
    WHERE django_content_type.id = target_type_id
        AND django_content_type.app_label = 'posts'
        AND lower(django_content_type.model) = 'comment'
);

ALTER TABLE public.notifications_flag
ADD CONSTRAINT reports__issuer_id__target_id__uniq UNIQUE NULLS NOT DISTINCT (
    issuer_id,
    target_user_id,
    target_post_id,
    target_comment_id
);

ALTER TABLE public.notifications_flag
ADD CONSTRAINT reports__target_id__chk CHECK (
    num_nonnulls(target_user_id, target_post_id, target_comment_id) = 1
);

ALTER TABLE public.notifications_flag
DROP COLUMN target_id;

ALTER TABLE public.notifications_flag
DROP COLUMN target_type_id;

CREATE INDEX reports__target_user_id__idx ON public.notifications_flag (target_user_id);

CREATE INDEX reports__target_post_id__idx ON public.notifications_flag (target_post_id);

CREATE INDEX reports__target_comment_id__idx ON public.notifications_flag (target_comment_id);

-- Rename constraints and indexes

CALL constraint__rename(
    'notifications_flag',
    'notifications_flag_issuer_id_%_fk_users_user_id',
    'reports__issuer_id__fk'
);

CALL index__rename(
    'notifications_flag_issuer_id_%',
    'reports__issuer_id__idx'
);

ALTER TABLE public.notifications_flag
DROP CONSTRAINT reports__issuer_id__fk;

ALTER TABLE public.notifications_flag
ADD CONSTRAINT reports__issuer_id__fk FOREIGN KEY (issuer_id) REFERENCES public.users_user(id) ON DELETE CASCADE;

-------------------
-- Notifications --
-------------------

CREATE VIEW public.notifications AS
SELECT
    posts_subscription.id,
    user_id,
    post_id,
    unread_comments.unread_count AS unread_comment_count,
    coalesce(last_comment.date_created, posts_post.date_created) AS date_updated
FROM public.posts_subscription
LEFT JOIN LATERAL (
    SELECT count(*) AS unread_count FROM public.posts_comment
    INNER JOIN public.posts_comment last_comment_seen ON last_comment_seen_id = last_comment_seen.id
    WHERE posts_comment.post_id = posts_subscription.post_id
        AND posts_subscription.last_comment_seen_id IS NOT NULL
        AND (posts_comment.date_created, posts_comment.id) > (last_comment_seen.date_created, last_comment_seen.id)
) AS unread_comments ON TRUE
LEFT JOIN LATERAL (
    SELECT posts_comment.date_created FROM public.posts_comment
    WHERE posts_comment.post_id = posts_subscription.post_id
    ORDER BY posts_comment.date_created DESC
    LIMIT 1
) AS last_comment ON TRUE
INNER JOIN public.posts_post ON posts_subscription.post_id = posts_post.id
    AND NOT posts_post.is_deleted;

-------------------
-- Rename tables --
-------------------

ALTER TABLE public.users_user
ALTER COLUMN id SET DEFAULT uuidv7();

ALTER TABLE public.users_user
RENAME TO users;

ALTER TABLE public.users_block
ALTER COLUMN id SET DEFAULT uuidv7();

ALTER TABLE public.users_block
RENAME TO blocks;

ALTER TABLE public.posts_post
ALTER COLUMN id SET DEFAULT uuidv7();

ALTER TABLE public.posts_post
RENAME TO posts;

ALTER TABLE public.posts_chapter
ALTER COLUMN id SET DEFAULT uuidv7();

ALTER TABLE public.posts_chapter
RENAME TO chapters;

ALTER TABLE public.posts_subscription
ALTER COLUMN id SET DEFAULT uuidv7();

ALTER TABLE public.posts_subscription
RENAME TO subscriptions;

ALTER TABLE public.posts_comment
ALTER COLUMN id SET DEFAULT uuidv7();

ALTER TABLE public.posts_comment
RENAME TO comments;

ALTER TABLE public.posts_vote
ALTER COLUMN id SET DEFAULT uuidv7();

ALTER TABLE public.posts_vote
RENAME TO votes;

ALTER TABLE public.notifications_remotemessaging
ALTER COLUMN id SET DEFAULT uuidv7();

ALTER TABLE public.notifications_remotemessaging
RENAME TO messaging_tokens;

ALTER TABLE public.notifications_flag
ALTER COLUMN id SET DEFAULT uuidv7();

ALTER TABLE public.notifications_flag
RENAME TO reports;

------------------------------
-- Functions and procedures --
------------------------------

CREATE FUNCTION chapters__on_insert()
RETURNS trigger
LANGUAGE plpgsql
AS
$$
BEGIN
    UPDATE public.chapters
    SET position = position + 1
    WHERE post_id = NEW.post_id AND position >= NEW.position;

    RETURN NEW;
END;
$$;

CREATE FUNCTION chapters__on_delete()
RETURNS trigger
LANGUAGE plpgsql
AS
$$
BEGIN
    UPDATE public.chapters
    SET position = position - 1
    WHERE post_id = OLD.post_id AND position > OLD.position;

    RETURN OLD;
END;
$$;

CREATE PROCEDURE chapters__move(post_id uuid, before integer, after integer)
LANGUAGE plpgsql
AS
$$
BEGIN
    INSERT INTO public.chapters (
        id,
        post_id,
        position,
        text,
        image_id,
        width,
        height
    )
    SELECT
        uuidv4(),
        chapters__move.post_id,
        CASE chapters__move.before < chapters__move.after
            WHEN TRUE THEN chapters__move.after + 1
            ELSE chapters__move.after
        END,
        text,
        image_id,
        width,
        height
    FROM public.chapters
    WHERE chapters.post_id = chapters__move.post_id
        AND chapters.position = chapters__move.before;

    DELETE FROM public.chapters
    WHERE chapters.post_id = chapters__move.post_id AND chapters.position = (
        CASE chapters__move.before < chapters__move.after
            WHEN TRUE THEN chapters__move.before
            ELSE chapters__move.before + 1
        END
    );
END;
$$;

CREATE FUNCTION subscriptions__on_insert()
RETURNS trigger
LANGUAGE plpgsql
AS
$$
BEGIN
    NEW.last_comment_seen_id := (
        SELECT id FROM public.comments
        WHERE post_id = NEW.post_id
        ORDER BY date_created DESC, id DESC
        LIMIT 1
    );

    RETURN NEW;
END;
$$;

CREATE FUNCTION users__ban(user_id uuid, ban_end timestamp with time zone)
RETURNS public.users
LANGUAGE plpgsql
AS
$$
DECLARE
    result_user users;
BEGIN
    UPDATE public.users
    SET
        ban_count = LEAST(ban_count + 1, 2),
        date_ban_end = (CASE ban_count WHEN 0 THEN ban_end END)
    WHERE id = users__ban.user_id
    RETURNING * INTO result_user;

    RETURN result_user;
END;
$$;

CREATE FUNCTION votes__cast(user_id uuid, post_id uuid, spread integer)
RETURNS public.votes
LANGUAGE plpgsql
AS
$$
DECLARE
    result_vote public.votes;
BEGIN
    IF votes__cast.user_id = (SELECT author_id FROM public.posts WHERE id = votes__cast.post_id) THEN
        RETURN NULL;
    END IF;

    INSERT INTO public.votes (user_id, post_id, spread)
    VALUES (votes__cast.user_id, votes__cast.post_id, votes__cast.spread::bool)
    ON CONFLICT DO NOTHING
    RETURNING * INTO result_vote;

    IF found THEN
        UPDATE public.posts
        SET life = life + votes__cast.spread
        WHERE id = votes__cast.post_id;
    ELSE
        SELECT * INTO result_vote FROM public.votes
        WHERE votes.user_id = votes__cast.user_id AND votes.post_id = votes__cast.post_id;
    END IF;

    RETURN result_vote;
END;
$$;

--------------
-- Triggers --
--------------

CREATE TRIGGER chapters__insert
BEFORE INSERT ON public.chapters
FOR EACH ROW
EXECUTE FUNCTION chapters__on_insert();

CREATE TRIGGER chapters__delete
AFTER DELETE ON public.chapters
FOR EACH ROW
EXECUTE FUNCTION chapters__on_delete();

CREATE TRIGGER subscriptions__insert
BEFORE INSERT ON public.subscriptions
FOR EACH ROW
EXECUTE FUNCTION subscriptions__on_insert();

---------------------
-- Drop old tables --
---------------------

DROP TABLE public.auth_group_permissions CASCADE;
DROP TABLE public.auth_group CASCADE;
DROP TABLE public.auth_permission CASCADE;
DROP TABLE public.core_cachedrequest CASCADE;
DROP TABLE public.django_celery_beat_clockedschedule CASCADE;
DROP TABLE public.django_celery_beat_crontabschedule CASCADE;
DROP TABLE public.django_celery_beat_intervalschedule CASCADE;
DROP TABLE public.django_celery_beat_periodictask CASCADE;
DROP TABLE public.django_celery_beat_periodictasks CASCADE;
DROP TABLE public.django_celery_beat_solarschedule CASCADE;
DROP TABLE public.django_content_type CASCADE;
DROP TABLE public.django_migrations CASCADE;
DROP TABLE public.django_session CASCADE;
DROP TABLE public.notifications_apnstoken CASCADE;
DROP TABLE public.notifications_notification CASCADE;
DROP TABLE public.posts_stack CASCADE;
DROP TABLE public.posts_visibility CASCADE;
DROP TABLE public.users_connection CASCADE;
DROP TABLE public.users_user_groups CASCADE;
DROP TABLE public.users_user_user_permissions CASCADE;

---------------------------
-- Drop local procedures --
---------------------------

DROP PROCEDURE constraint__rename(table_name varchar, name_pattern varchar, new_name varchar);
DROP PROCEDURE index__rename(name_pattern varchar, new_name varchar);
