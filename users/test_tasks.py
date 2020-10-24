from datetime import timedelta

from django.contrib.auth import get_user_model
from django.utils.timezone import now

from .models import Token
from .tasks import cleanup_tokens, cleanup_users
from .tests import BaseUserTestCase


class TaskTestCase(BaseUserTestCase):
    def test_cleanup_users(self):
        get_user_model().objects.create_user(
            username="new",
            is_active=False,
            date_joined=now() - timedelta(days=1),
        )
        user_count = get_user_model().objects.count()
        cleanup_users.delay()
        self.assertEqual(get_user_model().objects.count(), user_count - 1)

    def test_cleanup_users_too_soon(self):
        get_user_model().objects.create_user(
            username="new",
            is_active=False,
            date_joined=now() - timedelta(hours=23),
        )
        user_count = get_user_model().objects.count()
        cleanup_users.delay()
        self.assertEqual(get_user_model().objects.count(), user_count)

    def test_cleanup_tokens(self):
        token = Token.objects.create(user=self.main_user)
        token_count = Token.objects.count()
        last_used = now() - timedelta(weeks=12)
        Token.objects.filter(key=token.key).update(date_last_used=last_used)
        cleanup_tokens.delay()
        self.assertEqual(Token.objects.count(), token_count - 1)

    def test_cleanup_tokens_too_soon(self):
        token = Token.objects.create(user=self.main_user)
        token_count = Token.objects.count()
        last_used = now() - timedelta(weeks=11)
        Token.objects.filter(key=token.key).update(date_last_used=last_used)
        cleanup_tokens.delay()
        self.assertEqual(Token.objects.count(), token_count)
