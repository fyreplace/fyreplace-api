from django.conf import settings
from django.db import IntegrityError
from django.db.models import F
from django.db.models.signals import post_delete, post_save, pre_save
from django.db.transaction import atomic
from django.dispatch import receiver

from core.signals import post_soft_delete
from notifications.tasks import (
    send_notifications,
    send_remote_notifications_comment_change,
)

from .models import Chapter, Comment, Post, Subscription, Vote
from .tasks import remove_post_data


@receiver(post_soft_delete, sender=Post)
def on_post_post_soft_delete(instance: Post, **kwargs):
    remove_post_data.delay(post_id=str(instance.id))


@receiver(post_save, sender=Chapter)
def on_chapter_post_save(instance: Chapter, **kwargs):
    if len(instance.position) > 30:
        instance.post.normalize_chapters()


@receiver(post_delete, sender=Chapter)
def on_chapter_post_delete(instance: Chapter, **kwargs):
    instance.image.delete(save=False)


@receiver(post_save, sender=Comment)
def on_comment_post_save(instance: Comment, created: bool, **kwargs):
    if created:
        Subscription.objects.filter(
            user=instance.author, post=instance.post
        ).update_or_create(
            user=instance.author,
            post=instance.post,
            defaults={"last_comment_seen": instance},
        )

        (
            send_notifications.si(comment_id=str(instance.id), new_notifications=True)
            | send_remote_notifications_comment_change.si(comment_id=str(instance.id))
        ).delay()


@receiver(post_soft_delete, sender=Comment)
def on_comment_post_soft_delete(instance: Comment, **kwargs):
    (
        send_notifications.si(comment_id=str(instance.id), new_notifications=False)
        | send_remote_notifications_comment_change.si(comment_id=str(instance.id))
    ).delay()


@receiver(pre_save, sender=Vote)
def on_vote_pre_save(instance: Vote, **kwargs):
    if instance.user == instance.post.author:
        raise IntegrityError


@receiver(post_save, sender=Vote)
@atomic
def on_vote_post_save(instance: Vote, created: bool, **kwargs):
    if not created:
        return

    if instance.spread:
        Post.objects.filter(id=instance.post_id).update(
            life=F("life") + settings.FYREPLACE_POST_SPREAD_LIFE
        )

    instance.user.stack.posts.remove(instance.post)
