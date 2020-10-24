from django.contrib.auth import get_user_model
from django.db import DatabaseError

from core.tests import BaseTestCase

from .models import Token


def make_email(username: str) -> str:
    return f"{username}@example.com"


class BaseUserTestCase(BaseTestCase):
    MAIN_USER_PASSWORD = "Main user's password"
    OTHER_MAIN_PASSWORD = "Other user's password"
    STRONG_PASSWORD = "Some strong password!"

    def setUp(self):
        self.main_user = get_user_model().objects.create_user(
            username="main",
            email=make_email("main"),
            password=self.MAIN_USER_PASSWORD,
        )
        self.other_user = get_user_model().objects.create_user(
            username="other",
            email=make_email("other"),
            password=self.OTHER_MAIN_PASSWORD,
        )

    def tearDown(self):
        try:
            self.main_user.refresh_from_db()

            if self.main_user.avatar:
                self.main_user.avatar.delete()
        except DatabaseError:
            pass


class AuthenticatedTestCase(BaseUserTestCase):
    def setUp(self):
        super().setUp()
        token = Token.objects.create(user=self.main_user)
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {token}")
