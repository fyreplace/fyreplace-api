from celery import shared_task
from django.conf import settings
from django.contrib.contenttypes.models import ContentType
from django.db import IntegrityError
from django.db.transaction import atomic

from posts.models import Comment, Post, Subscription

from .models import ApnsToken, Notification
from .remote import apns, fcm


@shared_task(autoretry_for=[IntegrityError], retry_backoff=True)
def send_notifications(comment_id: str, new_notifications: bool):
    comment = Comment.objects.get(id=comment_id)
    subscriptions = Subscription.objects.filter(post_id=comment.post_id).exclude(
        user_id=comment.author_id
    )

    for subscription_id in subscriptions.values_list("id", flat=True):
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
def send_remote_notifications_comment_change(comment_id: str):
    comment = Comment.objects.get(id=comment_id)

    if comment.post.subscribers.exists():
        apns.send_remote_notifications_comment_change(comment)
        fcm.send_remote_notifications_comment_change(comment)


@shared_task(autoretry_for=[IntegrityError], retry_backoff=True)
def send_remote_notifications_comment_acknowledgement(comment_id: str, user_id: str):
    comment = Comment.objects.get(id=comment_id)
    apns.send_remote_notifications_comment_acknowledgement(comment, user_id)
    fcm.send_remote_notifications_comment_acknowledgement(comment, user_id)


@shared_task(autoretry_for=[IntegrityError], retry_backoff=True)
@atomic
def refresh_apns_token():
    if token := ApnsToken.objects.first():
        token.delete()

    ApnsToken.objects.create(token=apns.make_jwt())
