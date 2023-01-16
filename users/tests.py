from django.contrib.auth import get_user_model
from django.db import DatabaseError

from core.tests import BaseAPITestCase

from .models import Connection


def make_email(username: str) -> str:
    return f"{username}@fyreplace.app"


class BaseUserAPITestCase(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.main_user = get_user_model().objects.create_user(
            username="main",
            email=make_email("main"),
            bio="Main bio",
        )
        self.other_user = get_user_model().objects.create_user(
            username="other",
            email=make_email("other"),
            bio="Other bio",
        )

    def tearDown(self):
        super().tearDown()

        for user in [self.main_user, self.other_user]:
            try:
                user.refresh_from_db()
                user.delete()
            except DatabaseError:
                pass

    def set_credential_token(self, token: str):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")


class AuthenticatedAPITestCase(BaseUserAPITestCase):
    def setUp(self):
        super().setUp()
        self.main_connection = Connection.objects.create(user=self.main_user)
        self.set_credential_token(self.main_connection.get_token())


class BaseUserTestCase(BaseAPITestCase):
    def setUp(self):
        super().setUp()
        self.main_user = get_user_model().objects.create_user(
            username="main",
            email=make_email("main"),
        )
        self.other_user = get_user_model().objects.create_user(
            username="other",
            email=make_email("other"),
        )

    def tearDown(self):
        super().tearDown()

        for user in [self.main_user, self.other_user]:
            try:
                user.refresh_from_db()
                user.delete()
            except DatabaseError:
                pass


class AuthenticatedTestCase(BaseUserTestCase):
    def setUp(self):
        super().setUp()
        self.main_connection = Connection.objects.create(user=self.main_user)
