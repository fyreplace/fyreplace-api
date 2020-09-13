from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractUser
from django.db.models.signals import ModelSignal, post_delete, post_save
from django.dispatch import receiver

from core.signals import post_soft_delete

from .tasks import (
    fetch_default_user_avatar,
    remove_user_data,
    send_user_activation_email,
)

pre_ban = ModelSignal(use_caching=True)
post_ban = ModelSignal(use_caching=True)


@receiver(post_save, sender=get_user_model())
def on_user_post_save(instance: AbstractUser, created: bool, **kwargs):
    if created:
        user_id = str(instance.id)
        fetch_default_user_avatar.delay(user_id=user_id)

        if not instance.is_active:
            send_user_activation_email.delay(user_id=user_id)
    elif instance.is_deleted and instance.is_active:
        instance.is_active = False
        instance.save()


@receiver(post_delete, sender=get_user_model())
def on_user_post_delete(instance: AbstractUser, **kwargs):
    instance.avatar.delete(save=False)


@receiver(post_soft_delete, sender=get_user_model())
def on_user_post_soft_delete(instance: AbstractUser, **kwargs):
    instance.username = None
    instance.email = None
    instance.set_unusable_password()
    instance.avatar.delete(save=False)
    instance.bio = ""
    instance.save()
    remove_user_data.delay(user_id=str(instance.id))
