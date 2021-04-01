from datetime import datetime
from typing import Callable, List

from django.conf import settings
from django.contrib.auth import get_user_model
from django.utils.translation import gettext as _

from core import jwt
from core.emails import Email
from protos import user_pb2_grpc


def deep_link(method: Callable) -> str:
    return f"{settings.APP_NAME}:///{method.__qualname__}"


class BaseUserEmail(Email):
    @property
    def recipients(self) -> List[str]:
        return [self.user.email]

    @property
    def context(self) -> dict:
        link = f"{deep_link(self.method)}?jwt={self.token}"
        return {"app_name": settings.PRETTY_APP_NAME, "link": link}

    @property
    def payload_extras(self) -> dict:
        return {}

    @property
    def token(self) -> str:
        payload = {
            "timestamp": datetime.utcnow().timestamp(),
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
    def template(self) -> str:
        return "account_activation"

    @property
    def subject(self) -> str:
        return _(f"{settings.PRETTY_APP_NAME} account activation")

    @property
    def method(self) -> Callable:
        return user_pb2_grpc.AccountService.ConfirmActivation


class AccountRecoveryEmail(BaseUserEmail):
    @property
    def template(self) -> str:
        return "account_recovery"

    @property
    def subject(self) -> str:
        return _(f"{settings.PRETTY_APP_NAME} account recovery")

    @property
    def payload_extras(self):
        return {"email": self.user.email}

    @property
    def method(self) -> Callable:
        return user_pb2_grpc.AccountService.ConfirmRecovery


class UserEmailUpdateEmail(BaseUserEmail):
    @property
    def template(self) -> str:
        return "user_email_update"

    @property
    def subject(self) -> str:
        return _(f"{settings.PRETTY_APP_NAME} account email confirmation")

    @property
    def recipients(self) -> List[str]:
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
