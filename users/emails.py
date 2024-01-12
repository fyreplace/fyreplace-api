import io
from base64 import b64encode
from datetime import datetime, timezone
from typing import Callable

import qrcode
from django.conf import settings
from django.contrib.auth import get_user_model
from django.utils.translation import gettext as _

from core import jwt
from core.emails import Email
from protos import user_pb2_grpc


def deep_link(method: Callable, http: bool = True) -> str:
    scheme = "https" if http else settings.APP_NAME
    host = settings.EMAIL_LINKS_DOMAIN if http else ""
    return f"{scheme}://{host}/{method.__qualname__}"


def qr_data(link: str) -> str:
    code = qrcode.make(link)
    data = io.BytesIO()
    code.save(data)
    data.seek(0)
    return b64encode(data.read()).decode("ascii")


class BaseUserEmail(Email):
    @property
    def recipients(self) -> list[str]:
        return [self.user.email]

    @property
    def context(self) -> dict:
        http_link = f"{deep_link(self.method)}#{self.token}"
        app_link = f"{deep_link(self.method, http=False)}#{self.token}"
        return {
            "app_name": settings.PRETTY_APP_NAME,
            "link": http_link,
            "link_qr_data": qr_data(app_link),
        }

    @property
    def payload_extras(self) -> dict:
        return {}

    @property
    def token(self) -> str:
        payload = {
            "timestamp": datetime.now(timezone.utc).timestamp(),
            "user_id": str(self.user.id),
            **self.payload_extras,
        }

        return jwt.encode(payload)

    @property
    def method(self) -> Callable:
        raise NotImplementedError

    def __init__(self, user_id: str):
        self.user = get_user_model().objects.get(id=user_id)


class AccountActivationEmail(BaseUserEmail):
    @property
    def subject(self) -> str:
        return _(f"{settings.PRETTY_APP_NAME} account activation")

    @property
    def method(self) -> Callable:
        return user_pb2_grpc.AccountService.ConfirmActivation


class AccountConnectionEmail(BaseUserEmail):
    @property
    def subject(self) -> str:
        return _(f"{settings.PRETTY_APP_NAME} account connection")

    @property
    def payload_extras(self) -> dict:
        extras = super().payload_extras
        extras["connection_token"] = str(self.user.connection_token)
        return extras

    @property
    def method(self) -> Callable:
        return user_pb2_grpc.AccountService.ConfirmConnection


class UserEmailUpdateEmail(BaseUserEmail):
    @property
    def subject(self) -> str:
        return _(f"{settings.PRETTY_APP_NAME} account email confirmation")

    @property
    def recipients(self) -> list[str]:
        return [self.email]

    @property
    def payload_extras(self) -> dict:
        return {"email": self.email}

    @property
    def method(self) -> Callable:
        return user_pb2_grpc.UserService.ConfirmEmailUpdate

    def __init__(self, user_id: str, email: str):
        super().__init__(user_id)
        self.email = email


class UserBannedEmail(BaseUserEmail):
    @property
    def subject(self) -> str:
        return _(f"{settings.PRETTY_APP_NAME} ban notice")

    @property
    def context(self) -> dict:
        return {"date_ban_end": self.user.date_ban_end}
