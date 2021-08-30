from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractUser
from django.db import IntegrityError
from django.db.models import F
from django.db.models.signals import ModelSignal, post_delete, post_save, pre_save
from django.db.transaction import atomic
from django.dispatch import receiver

from core.signals import post_soft_delete

from .models import Chapter, Comment, Post, Stack, Vote
from .tasks import remove_post_data, remove_user_data

fetched = ModelSignal(use_caching=True)


@receiver(post_save, sender=get_user_model())
def on_user_post_save(instance: AbstractUser, created: bool, **kwargs):
    if created:
        Stack.objects.create(user=instance)


@receiver(post_soft_delete, sender=get_user_model())
def on_user_post_soft_delete(instance: AbstractUser, **kwargs):
    remove_user_data.delay(user_id=str(instance.id))


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
        instance.post.subscribers.add(instance.author)


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
        Post.objects.filter(id=instance.post_id).update(life=F("life") + 4)

    instance.user.stack.posts.remove(instance.post)
