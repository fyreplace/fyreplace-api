from celery import shared_task
from django.contrib.contenttypes.models import ContentType
from django.db import IntegrityError
from django.db.transaction import atomic

from posts.models import Comment, Post, Subscription

from .models import Flag, Notification


@shared_task(autoretry_for=[IntegrityError], retry_backoff=True)
def send_notifications(comment_id: str, new_notifications: bool):
    comment = Comment.objects.get(id=comment_id)

    for subscription_id in (
        Subscription.objects.filter(post_id=comment.post_id)
        .exclude(user_id=comment.author_id)
        .values_list("id", flat=True)
    ):
        with atomic():
            notification_data = {
                "subscription_id": subscription_id,
                "target_type": ContentType.objects.get_for_model(Post),
                "target_id": comment.post_id,
            }

            if new_notifications:
                notification, created = Notification.objects.get_or_create(
                    **notification_data
                )
            else:
                notification = Notification.objects.filter(**notification_data).first()
                created = False

            if notification and not created:
                notification.save()


@shared_task(autoretry_for=[IntegrityError], retry_backoff=True)
@atomic
def report_content(content_type_id: int, target_id: str, reporter_id: str):
    Flag.objects.get_or_create(
        issuer_id=reporter_id,
        target_type=ContentType.objects.get_for_id(content_type_id),
        target_id=target_id,
    )
