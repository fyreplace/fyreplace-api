from datetime import timedelta

from celery import shared_task
from django.utils.timezone import now

from .models import Chapter, Comment, Post, Stack, Visibility


@shared_task
def cleanup_stacks():
    deadline = now() - timedelta(days=1)

    while stacks := Stack.objects.filter(
        date_last_filled__lte=deadline, posts__isnull=False
    ):
        for stack in stacks[:100]:
            stack.drain()


@shared_task
def remove_user_data(user_id: str):
    while posts := Post.objects.filter(author_id=user_id, is_deleted=False):
        for post in posts[:100]:
            post.soft_delete()

    while comments := Comment.objects.filter(author_id=user_id, is_deleted=False):
        for comment in comments[:100]:
            comment.soft_delete()


@shared_task
def remove_post_data(post_id: str):
    Chapter.objects.filter(post_id=post_id).delete()
    Comment.objects.filter(post_id=post_id).delete()
    Visibility.objects.filter(post__is_deleted=True).delete()
