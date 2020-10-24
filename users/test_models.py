from datetime import datetime, timedelta
from time import sleep

from django.db import IntegrityError
from django.utils.timezone import now

from .models import Block, Token
from .tests import BaseUserTestCase


class UserTestCase(BaseUserTestCase):
    def test_delete(self):
        Token.objects.create(user=self.main_user)
        token_count = Token.objects.count()
        self.main_user.delete()
        self.assertTrue(self.main_user.is_deleted)
        self.assertIsNotNone(self.main_user.id)
        self.assertIsNone(self.main_user.username)
        self.assertIsNone(self.main_user.email)
        self.assertFalse(self.main_user.has_usable_password())
        self.assertFalse(self.main_user.avatar)
        self.assertEqual(self.main_user.bio, "")
        self.assertEqual(self.main_user.blocked_users.count(), 0)
        self.assertEqual(
            Token.objects.filter(user=self.main_user).count(), token_count - 1
        )

    def test_ban(self):
        before = now()
        duration = timedelta(days=3)
        self.main_user.ban(duration)
        self.assertTrue(self.main_user.is_banned)
        self.assertAlmostEqual(
            self.main_user.date_ban_end.timestamp(),
            (before + duration).timestamp(),
            places=1,
        )

    def test_block_unique(self):
        self.main_user.blocked_users.add(self.other_user)

        with self.assertRaises(IntegrityError):
            Block.objects.create(issuer=self.main_user, target=self.other_user)


class TokenTestCase(BaseUserTestCase):
    def test_date_last_used(self):
        token = Token.objects.create(user=self.main_user)
        before = now()
        sleep(0.1)
        token.save()
        self.assertGreater(token.date_last_used, before)
