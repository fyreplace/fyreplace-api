from datetime import timedelta

from django.conf import settings
from django.contrib.auth import get_user_model
from django.utils.timezone import now

from .models import Connection
from .tasks import cleanup_connections, cleanup_users
from .tests import BaseUserTestCase


class Task_cleanup_users(BaseUserTestCase):
    def test(self):
        get_user_model().objects.create_user(
            username="new",
            is_active=False,
            date_joined=now() - settings.FYREPLACE_INACTIVE_USER_DURATION,
        )
        user_count = get_user_model().existing_objects.count()
        cleanup_users.delay()
        self.assertEqual(get_user_model().existing_objects.count(), user_count - 1)

    def test_too_soon(self):
        get_user_model().objects.create_user(
            username="new",
            is_active=False,
            date_joined=now()
            - (settings.FYREPLACE_INACTIVE_USER_DURATION - timedelta(hours=1)),
        )
        user_count = get_user_model().existing_objects.count()
        cleanup_users.delay()
        self.assertEqual(get_user_model().existing_objects.count(), user_count)


class Task_cleanup_connections(BaseUserTestCase):
    def test(self):
        connection = Connection.objects.create(user=self.main_user)
        connection_count = Connection.objects.count()
        last_used = now() - settings.FYREPLACE_CONNECTION_DURATION
        Connection.objects.filter(id=connection.id).update(date_last_used=last_used)
        cleanup_connections.delay()
        self.assertEqual(Connection.objects.count(), connection_count - 1)

    def test_too_soon(self):
        connection = Connection.objects.create(user=self.main_user)
        connection_count = Connection.objects.count()
        last_used = now() - (settings.FYREPLACE_CONNECTION_DURATION - timedelta(days=1))
        Connection.objects.filter(id=connection.id).update(date_last_used=last_used)
        cleanup_connections.delay()
        self.assertEqual(Connection.objects.count(), connection_count)
