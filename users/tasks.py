import hashlib
from datetime import timedelta
from urllib.parse import urljoin

import httpx
from celery import shared_task
from django.conf import settings
from django.contrib.auth import get_user_model
from django.core.files.images import ImageFile
from django.core.files.temp import NamedTemporaryFile
from django.utils.timezone import now
from django.utils.translation import gettext as _
from rest_framework.status import HTTP_404_NOT_FOUND

from .emails import UserActivationEmail, UserEmailConfirmationEmail, UserRecoveryEmail
from .models import Token


@shared_task
def cleanup_users():
    deadline = now() - timedelta(days=1)
    get_user_model().objects.filter(
        date_joined__lte=deadline, is_active=False, is_deleted=False
    ).delete()


@shared_task
def cleanup_tokens():
    deadline = now() - timedelta(weeks=12)
    Token.objects.filter(date_last_used__lte=deadline).delete()


@shared_task
def remove_user_data(user_id: str):
    get_user_model().objects.get(id=user_id).blocked_users.clear()
    Token.objects.filter(user_id=user_id).delete()


@shared_task
def fetch_default_user_avatar(user_id: str):
    user = get_user_model().objects.get(id=user_id)

    if user.avatar:
        return

    email_hash = hashlib.md5(user.email.encode()).hexdigest()
    avatar_url = urljoin(settings.GRAVATAR_BASE_URL, f"avatar/{email_hash}")
    response = httpx.get(f"{avatar_url}?d={HTTP_404_NOT_FOUND}&s=256")

    if response.is_error:
        return

    data = response.read()
    temp_file = NamedTemporaryFile("wb+", delete=True)
    temp_file.write(data)
    temp_file.flush()
    user.avatar.delete(save=False)
    user.avatar = ImageFile(file=temp_file, name=user_id)
    user.save()


@shared_task
def send_user_activation_email(user_id: str):
    UserActivationEmail(user_id).send()


@shared_task
def send_user_email_confirmation_email(user_id: str, email: str):
    UserEmailConfirmationEmail(user_id, email).send()


@shared_task
def send_user_recovery_email(email: str):
    if get_user_model().objects.filter(email=email).exists():
        UserRecoveryEmail(email).send()
