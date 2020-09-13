from datetime import datetime
from typing import List

from django.conf import settings
from django.contrib.auth import get_user_model
from django.utils.translation import gettext as _
from jose import jwt

from core.emails import Email
from core.views import deep_link


class JWTEmail(Email):
    @property
    def context(self) -> dict:
        payload = {
            "timestamp": datetime.utcnow().timestamp(),
            **self.payload_extras,
        }

        json_web_token = jwt.encode(payload, key=settings.SECRET_KEY)
        link_base = deep_link(
            self.uri["url_name"],
            *self.uri.get("args", []),
            **self.uri.get("kwargs", {}),
        )
        link = f"{link_base}?jwt={json_web_token}"
        return {"app_name": settings.PRETTY_APP_NAME, "link": link}

    @property
    def payload_extras(self):
        raise NotImplementedError

    @property
    def uri(self) -> dict:
        raise NotImplementedError


class BaseUserEmail(JWTEmail):
    @property
    def recipients(self):
        user = get_user_model().objects.get(id=self.user_id)
        return [user.email]

    @property
    def payload_extras(self):
        return {"user_id": self.user_id}

    def __init__(self, user_id: str):
        self.user_id = user_id


class UserActivationEmail(BaseUserEmail):
    @property
    def template(self) -> str:
        return "user_activation"

    @property
    def subject(self) -> str:
        return _(f"{settings.PRETTY_APP_NAME} account activation")

    @property
    def uri(self) -> dict:
        return {"url_name": "users:user-activation", "args": ["me"]}


class UserEmailConfirmationEmail(BaseUserEmail):
    @property
    def template(self) -> str:
        return "user_email_confirmation"

    @property
    def subject(self) -> str:
        return _(f"{settings.PRETTY_APP_NAME} account email confirmation")

    @property
    def recipients(self) -> List[str]:
        return [self.email]

    @property
    def payload_extras(self):
        return {**super().payload_extras, "email": self.email}

    @property
    def uri(self) -> dict:
        return {"url_name": "users:user-email-confirmation", "args": ["me"]}

    def __init__(self, user_id: str, email: str):
        super().__init__(user_id)
        self.email = email


class UserRecoveryEmail(JWTEmail):
    @property
    def template(self) -> str:
        return "user_recovery"

    @property
    def subject(self) -> str:
        return _(f"{settings.PRETTY_APP_NAME} account recovery")

    @property
    def recipients(self):
        return [self.email]

    @property
    def payload_extras(self):
        return {"email": self.email}

    @property
    def uri(self) -> dict:
        return {"url_name": "users:token-list"}

    def __init__(self, email: str):
        self.email = email
