from datetime import timedelta

from django.db import IntegrityError
from django.utils.timezone import now

from .models import Block, Connection
from .tests import BaseUserTestCase


class User_ban(BaseUserTestCase):
    def test(self):
        before = now()
        duration = timedelta(days=3)
        self.main_user.ban(duration)
        self.assertTrue(self.main_user.is_banned)
        self.assertAlmostEqual(
            self.main_user.date_ban_end, (before + duration), delta=timedelta(seconds=1)
        )

    def test_forever(self):
        Connection.objects.create(user=self.main_user)
        self.main_user.ban()
        self.assertTrue(self.main_user.is_banned)
        self.assertIsNone(self.main_user.date_ban_end)
        self.assertEqual(Connection.objects.filter(user=self.main_user).count(), 0)


class User_block(BaseUserTestCase):
    def test_unique(self):
        self.main_user.blocked_users.add(self.other_user)

        with self.assertRaises(IntegrityError):
            Block.objects.create(issuer=self.main_user, target=self.other_user)

    def test_issuer_not_target(self):
        with self.assertRaises(IntegrityError):
            self.main_user.blocked_users.add(self.main_user)
