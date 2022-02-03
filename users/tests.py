from typing import Optional

from django.contrib.auth import get_user_model
from django.contrib.auth.base_user import AbstractBaseUser
from django.db import DatabaseError

from core import jwt
from core.tests import BaseTestCase, FakeContext

from .models import Connection


def make_email(username: str) -> str:
    return f"{username}@example.com"


class UserContext(FakeContext):
    def set_user(self, user: Optional[AbstractBaseUser]):
        if user:
            payload = {"user_id": str(user.id)}

            if connection := Connection.objects.filter(user=user).first():
                payload["connection_id"] = str(connection.id)

            token = jwt.encode(payload)
            self._invocation_metadata["authorization"] = f"Bearer {token}"
            self.caller = user
            self.caller_connection = connection
        else:
            del self._invocation_metadata["authorization"]
            self.caller = None
            self.caller_connection = None


class BaseUserTestCase(BaseTestCase):
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
        self.grpc_context = UserContext()
        self.grpc_context.set_user(self.main_user)
