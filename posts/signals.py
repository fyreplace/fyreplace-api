from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractUser
from django.db import IntegrityError
from django.db.models import F
from django.db.models.signals import post_delete, post_save, pre_save
from django.dispatch import receiver

from core.signals import post_soft_delete

from .models import Chunk, Comment, Post, Stack, Vote
from .tasks import remove_post_data, remove_user_data


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
    instance.life = 0
    instance.save()


@receiver(pre_save, sender=Chunk)
def on_chunk_pre_save(instance: Chunk, **kwargs):
    chunk = instance.post.chunks.filter(id=instance.id).first()

    if chunk is not None and instance.position != chunk.position:
        instance.post.chunks.filter(id=instance.id).update(position=-1)
        remaining_chunks = instance.post.chunks.filter(position__gt=chunk.position)
        remaining_chunks.update(position=F("position") - 1)

    existing_chunk = instance.post.chunks.filter(position=instance.position).first()

    if existing_chunk is not None and existing_chunk != instance:
        existing_chunk.position += 1
        existing_chunk.save()


@receiver(post_delete, sender=Chunk)
def on_chunk_post_delete(instance: Chunk, **kwargs):
    instance.image.delete(save=False)
    remaining_chunks = instance.post.chunks.filter(position__gt=instance.position)
    remaining_chunks.update(position=F("position") - 1)


@receiver(post_save, sender=Comment)
def on_comment_post_save(instance: Comment, created: bool, **kwargs):
    if created:
        instance.post.subscribers.add(instance.author)


@receiver(post_soft_delete, sender=Comment)
def on_comment_post_soft_delete(instance: Comment, **kwargs):
    instance.text = ""
    instance.save()


@receiver(pre_save, sender=Vote)
def on_vote_pre_save(instance: Vote, **kwargs):
    if instance.user == instance.post.author:
        raise IntegrityError


@receiver(post_save, sender=Vote)
def on_vote_post_save(instance: Vote, created: bool, **kwargs):
    if not created:
        return

    if instance.spread:
        instance.post.life += 4
        instance.post.save()

    instance.user.stack.posts.remove(instance.post)
