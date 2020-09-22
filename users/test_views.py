from base64 import b64encode
from datetime import datetime, timedelta

from django.conf import settings
from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractUser
from django.utils.timezone import now
from jose import jwt
from rest_framework import status
from rest_framework.reverse import reverse

from core.tests import get_asset

from .emails import UserActivationEmail, UserEmailConfirmationEmail, UserRecoveryEmail
from .models import Token
from .serializers import CreateUserSerializer
from .tests import AuthenticatedTestCase, BaseUserTestCase, make_email


class UserTestCase(AuthenticatedTestCase):
    def test_retrieve(self):
        url = reverse("users:user-detail", args=[self.other_user.id])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(
            response.data,
            {
                "id": str(self.other_user.id),
                "username": self.other_user.username,
                "date_created": self.other_user.date_joined,
            },
        )

    def test_retrieve_banned(self):
        self.other_user.ban(timedelta(days=3))
        url = reverse("users:user-detail", args=[self.other_user.id])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(
            response.data,
            {
                "id": str(self.other_user.id),
                "username": self.other_user.username,
                "date_created": self.other_user.date_joined,
                "is_banned": True,
            },
        )

    def test_retrieve_me(self):
        url = reverse("users:user-detail", args=["me"])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data, self._get_main_user_data())

    def test_retrieve_deleted(self):
        self.other_user.delete()
        url = reverse("users:user-detail", args=[self.other_user.id])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_410_GONE)

    def test_retrieve_non_existent(self):
        url = reverse("users:user-detail", args=["non-existent"])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_update_bio(self):
        url = reverse("users:user-detail", args=["me"])
        data = {"bio": "Hello!"}
        response = self.client.patch(url, data)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data, {**data, **self._get_main_user_data()})

    def test_update_bio_too_long(self):
        url = reverse("users:user-detail", args=["me"])
        data = {"bio": "a" * (get_user_model().bio.field.max_length + 1)}
        response = self.client.patch(url, data)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_update_avatar(self):
        url = reverse("users:user-detail", args=["me"])

        for ext in ["jpg", "png", "webp", "gif"]:
            with open(get_asset(f"image.{ext}"), "rb") as image:
                data = {"avatar": image}
                response = self.client.patch(url, data)

            self.assertEqual(response.status_code, status.HTTP_200_OK)
            self.assertIn("avatar", response.data)
            self.assertRegex(response.data["avatar"], r".*\." + ext)

    def test_update_avatar_bad_format(self):
        url = reverse("users:user-detail", args=["me"])

        with open(get_asset("image.txt"), "r") as text:
            response = self.client.patch(url, {"avatar": text})
            self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_update_read_only_fields(self):
        url = reverse("users:user-detail", args=["me"])
        data = {"username": "someone", "date_joined": str(now())}
        response = self.client.patch(url, data)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data, self._get_main_user_data())

    def test_update_other(self):
        url = reverse("users:user-detail", args=[str(self.other_user.id)])
        data = {"bio": "Hello!"}
        response = self.client.patch(url, data)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_destroy(self):
        url = reverse("users:user-detail", args=["me"])
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.main_user.refresh_from_db()
        self.assertIsNone(self.main_user.username)
        self.assertIsNone(self.main_user.email)
        self.assertFalse(self.main_user.has_usable_password())
        self.assertFalse(self.main_user.is_active)
        self.assertTrue(self.main_user.is_deleted)

    def test_destroy_other(self):
        url = reverse("users:user-detail", args=[str(self.other_user.id)])
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_password_change(self):
        url = reverse("users:user-password-change", args=["me"])
        data = {"password": "The new password"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.main_user.refresh_from_db()
        self.assertTrue(self.main_user.check_password(data["password"]))

    def test_password_change_other(self):
        url = reverse("users:user-password-change", args=[str(self.other_user.id)])
        data = {"password": "The new password"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_email_change(self):
        url = reverse("users:user-email-change", args=["me"])
        new_email = make_email("new_email")
        data = {"email": new_email}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEmails([UserEmailConfirmationEmail(self.main_user.id, new_email)])

    def test_email_change_other(self):
        url = reverse("users:user-email-change", args=[str(self.other_user.id)])
        data = {"email": make_email("new_email")}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def _get_main_user_data(self):
        return {
            "id": str(self.main_user.id),
            "username": self.main_user.username,
            "email": self.main_user.email,
            "date_created": self.main_user.date_joined,
        }


class UserInteractionTestCase(AuthenticatedTestCase):
    def test_block(self):
        url = reverse("users:user-block", args=[str(self.other_user.id)])
        response = self.client.post(url)
        self.assertEqual(response.status_code, status.HTTP_205_RESET_CONTENT)
        self.assertEqual(self.main_user.blocked_users.count(), 1)
        self.assertEqual(self.main_user.blocked_users.first().id, self.other_user.id)

    def test_block_me(self):
        url = reverse("users:user-block", args=["me"])
        response = self.client.post(url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_block_non_existent(self):
        url = reverse("users:user-block", args=["non-existent"])
        response = self.client.post(url)
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_block_destroy(self):
        self.main_user.blocked_users.add(self.other_user)
        url = reverse("users:user-block", args=[str(self.other_user.id)])
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(self.main_user.blocked_users.count(), 0)

    def test_blocked(self):
        self.main_user.blocked_users.add(self.other_user)
        url = reverse("users:user-blocked")
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["count"], 1)
        self.assertEqual(
            response.data["results"],
            [{"id": str(self.other_user.id), "username": self.other_user.username}],
        )


class AccountTestCase(BaseUserTestCase):
    def test_activation(self):
        token = self._get_jwt(user_id=str(self._create_new_user().id))
        url = reverse("users:user-activation", args=["me"])
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Bearer {token}")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("key", response.data)
        self.assertIsInstance(response.data["key"], str)
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Bearer {token}")
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_activation_missing_user_id(self):
        token = self._get_jwt()
        url = reverse("users:user-activation", args=["me"])
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Bearer {token}")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_activation_too_late(self):
        token = self._get_jwt(
            timestamp=(now() - timedelta(hours=1)).timestamp(),
            user_id=str(self._create_new_user().id),
        )
        url = reverse("users:user-activation", args=["me"])
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Bearer {token}")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_activation_already_active_user(self):
        token = self._get_jwt(user_id=str(self.main_user.id))
        url = reverse("users:user-activation", args=["me"])
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Bearer {token}")
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_activation_id_mismatch(self):
        first_user = self._create_new_user("first")
        second_user = self._create_new_user("second")
        token = self._get_jwt(user_id=str(first_user.id))
        url = reverse("users:user-activation", args=[str(second_user.id)])
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Bearer {token}")
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_email_confirmation(self):
        token = self._get_jwt(
            user_id=str(self.main_user.id), email=self.main_user.email
        )
        url = reverse("users:user-email-confirmation", args=["me"])
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Bearer {token}")
        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_email_confirmation_id_mismatch(self):
        token = self._get_jwt(user_id=str(self.main_user.id))
        url = reverse("users:user-email-confirmation", args=[str(self.other_user.id)])
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Bearer {token}")
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def _create_new_user(self, username: str = "new") -> AbstractUser:
        serializer = CreateUserSerializer(
            data={
                "username": username,
                "email": make_email(username),
                "password": self.STRONG_PASSWORD,
            }
        )
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return serializer.instance

    def _get_jwt(self, **data) -> str:
        payload = {"timestamp": now().timestamp(), **data}
        return jwt.encode(payload, key=settings.SECRET_KEY)


class SetupTestCase(BaseUserTestCase):
    def test_create(self):
        url = reverse("users:user-list")
        data = {
            "username": "new",
            "email": make_email("new"),
            "password": self.STRONG_PASSWORD,
        }
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEmails([UserActivationEmail(response.data["id"])])

        for field in ["username", "email"]:
            self.assertEqual(response.data[field], data[field])

        self.assertIn("id", response.data)
        self.assertIsInstance(response.data["id"], str)
        self.assertIn("date_created", response.data)
        self.assertIsInstance(response.data["date_created"], datetime)
        user = get_user_model().objects.get(id=response.data["id"])
        self.assertFalse(user.is_staff)
        self.assertFalse(user.is_superuser)

    def test_create_username_too_short(self):
        url = reverse("users:user-list")
        data = {
            "username": "a",
            "email": make_email("bad"),
            "password": self.STRONG_PASSWORD,
        }
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_username_too_long(self):
        url = reverse("users:user-list")
        data = {
            "username": "a" * (get_user_model().username.field.max_length + 1),
            "email": make_email("new"),
            "password": self.STRONG_PASSWORD,
        }
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_bad_email(self):
        url = reverse("users:user-list")
        data = {
            "username": "bad",
            "email": "not-an-email",
            "password": self.STRONG_PASSWORD,
        }
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_bad_password(self):
        url = reverse("users:user-list")
        data = {
            "username": "bad",
            "email": make_email("bad"),
            "password": "password",
        }
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_recovery(self):
        url = reverse("users:user-recovery")
        data = {"email": self.main_user.email}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEmails([UserRecoveryEmail(self.main_user.email)])

    def test_recovery_unused_email(self):
        url = reverse("users:user-recovery")
        data = {"email": make_email("unused")}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_recovery_bad_email(self):
        url = reverse("users:user-recovery")
        data = {"email": "not-an-email"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)


class LoginTestCase(BaseUserTestCase):
    def test_create(self):
        url = reverse("users:token-list")
        data = self._get_encoded_credentials(
            self.main_user.username, self.MAIN_USER_PASSWORD
        )
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Basic {data}")
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn("key", response.data)
        self.assertIsInstance(response.data["key"], str)

    def test_create_bad_username(self):
        url = reverse("users:token-list")
        data = self._get_encoded_credentials("bad", self.MAIN_USER_PASSWORD)
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Basic {data}")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_create_bad_password(self):
        url = reverse("users:token-list")
        data = self._get_encoded_credentials(self.main_user.username, "bad")
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Basic {data}")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_create_from_recovery(self):
        url = reverse("users:token-list")
        payload = {
            "timestamp": now().timestamp(),
            "user_id": str(self.main_user.id),
        }
        token = jwt.encode(payload, key=settings.SECRET_KEY)
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Bearer {token}")
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn("key", response.data)
        self.assertIsInstance(response.data["key"], str)

    def test_create_from_recovery_non_existent(self):
        url = reverse("users:token-list")
        payload = {
            "timestamp": now().timestamp(),
            "user_id": "non-existent",
        }
        token = jwt.encode(payload, key=settings.SECRET_KEY)
        response = self.client.post(url, HTTP_AUTHORIZATION=f"Bearer {token}")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def _get_encoded_credentials(self, username: str, password: str) -> str:
        credentials = f"{username}:{password}"
        return b64encode(credentials.encode("utf-8")).decode("utf-8")


class LogoutTestCase(AuthenticatedTestCase):
    def test_destroy(self):
        token = Token.objects.filter(user=self.main_user).first()
        url = reverse("users:token-detail", args=[str(token.key)])
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertEqual(Token.objects.filter(user=self.main_user).count(), 0)

    def test_destroy_other(self):
        token = Token.objects.create(user=self.other_user)
        url = reverse("users:token-detail", args=[str(token.key)])
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
