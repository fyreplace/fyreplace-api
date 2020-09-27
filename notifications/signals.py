from typing import Union

from django.contrib.auth.models import AbstractUser
from django.db.models.signals import m2m_changed, post_save
from django.dispatch import receiver

from core.signals import fetched
from posts.models import Comment

from .models import Notification
from .tasks import (
    mark_notification_as_received,
    notify_post_subscribers,
    remove_comments_from_notifications,
)


@receiver(post_save, sender=Comment)
def on_comment_post_save(instance: Comment, created: bool, **kwargs):
    if created:
        notify_post_subscribers.delay(comment_id=str(instance.id))


@receiver(fetched, sender=Comment)
def on_comment_fetched(user: AbstractUser, pk_set: list, **kwargs):
    remove_comments_from_notifications.delay(user_id=str(user.id), comment_ids=pk_set)


@receiver(fetched, sender=Notification)
def on_notifications_fetched(user: AbstractUser, pk_set: list, **kwargs):
    mark_notification_as_received.delay(notification_ids=pk_set)


@receiver(m2m_changed, sender=Notification.comments.through)
def on_notification_m2m_changed(
    instance: Union[Notification, Comment], action: str, **kwargs
):
    if not isinstance(instance, Notification):
        return

    if action == "post_add":
        instance.save()
    elif action in ["post_delete", "post_clear"] and instance.comments.count() == 0:
        instance.delete()
