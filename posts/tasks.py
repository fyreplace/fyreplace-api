from datetime import timedelta

from celery import shared_task
from django.db.transaction import atomic
from django.utils.timezone import now

from .models import Chapter, Comment, Post, Stack, Visibility


@shared_task
def cleanup_stacks():
    deadline = now() - timedelta(days=1)

    while stack_ids := Stack.objects.filter(
        date_last_filled__lte=deadline, posts__isnull=False
    ).values("id"):
        stacks = Stack.objects.select_for_update().filter(id__in=stack_ids)

        with atomic():
            for stack in stacks[:100]:
                stack.drain()


@shared_task
def remove_post_data_for_user(user_id: str):
    while post_ids := Post.objects.filter(author_id=user_id, is_deleted=False).values(
        "id"
    ):
        posts = Post.objects.select_for_update().filter(id__in=post_ids)

        with atomic():
            for post in posts[:100]:
                post.soft_delete()

    while comment_ids := Comment.objects.filter(
        author_id=user_id, is_deleted=False
    ).values("id"):
        comments = Comment.objects.select_for_update().filter(id__in=comment_ids)

        with atomic():
            for comment in comments[:100]:
                comment.soft_delete()


@shared_task
def remove_post_data(post_id: str):
    Chapter.objects.filter(post_id=post_id).delete()
    Comment.objects.filter(post_id=post_id).delete()
    Visibility.objects.filter(post__is_deleted=True).delete()
