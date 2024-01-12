import hashlib
import io
from urllib.parse import urljoin

import magic
from celery import shared_task
from django.conf import settings
from django.contrib.auth import get_user_model
from django.core.files.images import ImageFile
from django.db.transaction import atomic
from django.utils.timezone import now
from requests import get

from .emails import (
    AccountActivationEmail,
    AccountConnectionEmail,
    UserBannedEmail,
    UserEmailUpdateEmail,
)
from .models import Connection


@shared_task
def cleanup_users():
    deadline = now() - settings.FYREPLACE_INACTIVE_USER_DURATION
    get_user_model().objects.filter(
        date_joined__lte=deadline, is_active=False, is_deleted=False
    ).delete()


@shared_task
def cleanup_connections():
    deadline = now() - settings.FYREPLACE_CONNECTION_DURATION
    Connection.objects.filter(date_last_used__lte=deadline).delete()


@shared_task
def remove_user_data(user_id: str):
    Connection.objects.filter(user_id=user_id).delete()


@shared_task
@atomic
def fetch_default_user_avatar(user_id: str):
    if not settings.GRAVATAR_BASE_URL:
        return

    user = get_user_model().objects.select_for_update().get(id=user_id)

    if not user or user.avatar:
        return

    email_hash = hashlib.md5(user.email.encode()).hexdigest()
    avatar_url = urljoin(settings.GRAVATAR_BASE_URL, f"avatar/{email_hash}")
    response = get(f"{avatar_url}?d=404&s=256")

    if not response.ok:
        return

    mime = magic.from_buffer(response.content, mime=True)

    if mime not in settings.VALID_IMAGE_MIMES:
        return

    user.avatar.delete(save=False)
    user.avatar = ImageFile(
        io.BytesIO(response.content), name=f"{user_id}.{mime.split('/')[-1]}"
    )
    user.save()


@shared_task
def lift_ban(user_id: str):
    get_user_model().objects.filter(id=user_id).update(
        is_banned=False, date_ban_end=None
    )


@shared_task
def send_account_activation_email(user_id: str):
    AccountActivationEmail(user_id).send()


@shared_task
def send_account_connection_email(user_id: str):
    AccountConnectionEmail(user_id).send()


@shared_task
def send_user_email_update_email(user_id: str, email: str):
    UserEmailUpdateEmail(user_id, email).send()


@shared_task
def send_user_banned_email(user_id: str):
    UserBannedEmail(user_id).send()


@shared_task
def use_connection(connection_id: int):
    Connection.objects.filter(id=connection_id).update(date_last_used=now())
