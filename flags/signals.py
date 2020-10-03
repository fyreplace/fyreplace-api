from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractUser
from django.db.models import Model
from django.dispatch import receiver

from core.signals import post_soft_delete
from users.signals import post_ban

from .models import Flag


@receiver(post_ban, sender=get_user_model())
def on_user_post_ban(instance: AbstractUser, **kwargs):
    Flag.objects.filter(target_id=instance.id).delete()


@receiver(post_soft_delete)
def on_post_soft_delete(instance: Model, **kwargs):
    Flag.objects.filter(target_id=instance.id).delete()
