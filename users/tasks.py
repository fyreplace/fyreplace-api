import hashlib
import io
from datetime import timedelta
from urllib.parse import urljoin

import httpx
import magic
from celery import shared_task
from django.conf import settings
from django.contrib.auth import get_user_model
from django.core.files.images import ImageFile
from django.utils.timezone import now

from .emails import AccountActivationEmail, AccountRecoveryEmail, UserEmailUpdateEmail
from .models import Connection


@shared_task
def cleanup_users():
    deadline = now() - timedelta(days=1)
    get_user_model().objects.filter(
        date_joined__lte=deadline, is_active=False, is_deleted=False
    ).delete()


@shared_task
def cleanup_connections():
    deadline = now() - timedelta(weeks=12)
    Connection.objects.filter(date_last_used__lte=deadline).delete()


@shared_task
def remove_user_data(user_id: str):
    Connection.objects.filter(user_id=user_id).delete()


@shared_task
def fetch_default_user_avatar(user_id: str):
    if not settings.GRAVATAR_BASE_URL:
        return

    user = get_user_model().objects.get(id=user_id)

    if user.avatar:
        return

    email_hash = hashlib.md5(user.email.encode()).hexdigest()
    avatar_url = urljoin(settings.GRAVATAR_BASE_URL, f"avatar/{email_hash}")
    response = httpx.get(f"{avatar_url}?d=404&s=256")

    if response.is_error:
        return

    data = response.read()
    mime = magic.from_buffer(data, mime=True)

    if mime not in settings.VALID_IMAGE_MIMES:
        return

    user.avatar.delete(save=False)
    user.avatar = ImageFile(io.BytesIO(data), name=f"{user_id}.{mime.split('/')[-1]}")
    user.save()


@shared_task
def send_account_activation_email(user_id: str):
    AccountActivationEmail(user_id).send()


@shared_task
def send_account_recovery_email(user_id: str):
    AccountRecoveryEmail(user_id).send()


@shared_task
def send_user_email_update_email(user_id: str, email: str):
    UserEmailUpdateEmail(user_id, email).send()


@shared_task
def use_connection(connection_id: int):
    for connection in Connection.objects.filter(id=connection_id):
        connection.save()
