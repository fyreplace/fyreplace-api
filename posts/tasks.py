from datetime import timedelta

from celery import shared_task
from django.utils.timezone import now

from users.models import Token

from .models import Chunk, Comment, Post, Stack


@shared_task
def cleanup_stacks():
    deadline = now() - timedelta(days=1)

    for stack in Stack.objects.filter(date_last_filled__lte=deadline):
        stack.drain()


@shared_task
def remove_user_data(user_id: str):
    for post in Post.objects.filter(author_id=user_id):
        post.soft_delete()

    for comment in Comment.objects.filter(author_id=user_id):
        comment.soft_delete()


@shared_task
def remove_post_data(post_id: str):
    Chunk.objects.filter(post_id=post_id).delete()
    Comment.objects.filter(post_id=post_id).delete()


@shared_task
def use_token(token_key: str):
    Token.objects.get(key=token_key).save()
