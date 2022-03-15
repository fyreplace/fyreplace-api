from typing import List

from celery import shared_task
from django.contrib.auth import get_user_model
from django.contrib.contenttypes.models import ContentType
from django.db import IntegrityError
from django.db.transaction import atomic

from posts.models import Comment, Post

from .models import CountUnit, Notification


@shared_task(autoretry_for=[IntegrityError], retry_backoff=True)
def send_notifications(comment_id: str):
    comment = Comment.objects.select_related().get(id=comment_id)

    for user_id in comment.post.subscribers.exclude(id=comment.author_id).values_list(
        "id", flat=True
    ):
        with atomic():
            notification, _ = Notification.objects.get_or_create(
                recipient_id=user_id,
                target_type=ContentType.objects.get_for_model(Post),
                target_id=comment.post_id,
            )

            CountUnit.objects.get_or_create(
                notification=notification,
                count_item_type=ContentType.objects.get_for_model(Comment),
                count_item_id=comment.id,
            )


@shared_task
def remove_comments_from_notifications(user_id: str, comment_ids: List[str]):
    CountUnit.objects.filter(
        notification__recipient_id=user_id,
        count_item_type=ContentType.objects.get_for_model(Comment),
        count_item_id__in=comment_ids,
    ).delete()


@shared_task(autoretry_for=[IntegrityError], retry_backoff=True)
@atomic
def report_content(content_type_id: int, target_id: str, reporter_id: str):
    flag, _ = Notification.objects.get_or_create(
        recipient=None,
        target_type=ContentType.objects.get_for_id(content_type_id),
        target_id=target_id,
    )
    CountUnit.objects.get_or_create(
        notification=flag,
        count_item_type=ContentType.objects.get_for_model(get_user_model()),
        count_item_id=reporter_id,
    )
