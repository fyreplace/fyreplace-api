from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractUser
from django.db.models.signals import ModelSignal, post_delete, post_save
from django.dispatch import receiver

from core.signals import post_soft_delete

from .tasks import remove_user_data

pre_ban = ModelSignal(use_caching=True)
post_ban = ModelSignal(use_caching=True)


@receiver(post_save, sender=get_user_model())
def on_user_post_save(instance: AbstractUser, created: bool, **kwargs):
    if instance.is_deleted and instance.is_active:
        instance.is_active = False
        instance.save()


@receiver(post_ban, sender=get_user_model())
def on_user_post_ban(instance: AbstractUser, **kwargs):
    remove_user_data.delay(user_id=str(instance.id))


@receiver(post_delete, sender=get_user_model())
def on_user_post_delete(instance: AbstractUser, **kwargs):
    instance.avatar.delete(save=False)


@receiver(post_soft_delete, sender=get_user_model())
def on_user_post_soft_delete(instance: AbstractUser, **kwargs):
    remove_user_data.delay(user_id=str(instance.id))
