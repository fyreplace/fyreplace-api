from django.conf import settings
from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractUser
from django.db.models.signals import ModelSignal, post_delete, post_save
from django.dispatch import receiver

from core.signals import post_soft_delete
from notifications.models import remove_notifications_for
from posts.models import Stack
from posts.tasks import remove_post_data_for_user

from .tasks import lift_ban, remove_user_data, send_user_banned_email

pre_ban = ModelSignal(use_caching=True)
post_ban = ModelSignal(use_caching=True)


@receiver(post_save, sender=get_user_model())
def on_user_post_save(instance: AbstractUser, created: bool, **kwargs):
    get_user_model().objects.filter(
        id=instance.id, is_deleted=True, is_active=True
    ).update(is_active=False)

    if created:
        Stack.objects.create(user=instance)


@receiver(post_ban, sender=get_user_model())
def on_user_post_ban(instance: AbstractUser, **kwargs):
    remove_notifications_for(instance)
    remove_user_data.delay(user_id=str(instance.id))
    send_user_banned_email.delay(instance.id)

    if instance.date_ban_end and not settings.IS_TESTING:
        lift_ban.apply_async(
            kwargs={"user_id": str(instance.id)}, eta=instance.date_ban_end
        )


@receiver(post_delete, sender=get_user_model())
def on_user_post_delete(instance: AbstractUser, **kwargs):
    instance.avatar.delete(save=False)


@receiver(post_soft_delete, sender=get_user_model())
def on_user_post_soft_delete(instance: AbstractUser, **kwargs):
    remove_user_data.delay(user_id=str(instance.id))
    remove_post_data_for_user.delay(user_id=str(instance.id))
