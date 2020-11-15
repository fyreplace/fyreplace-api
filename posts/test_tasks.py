from datetime import timedelta

from django.utils.timezone import now

from .models import Stack
from .tasks import cleanup_stacks
from .tests import PublishedPostTestCase


class TaskTestCase(PublishedPostTestCase):
    def setUp(self):
        super().setUp()
        self.stack = Stack.objects.get(user=self.other_user)
        self.stack.fill()

    def test_cleanup_stacks(self):
        self.assertEqual(self.stack.posts.count(), 1)
        Stack.objects.filter(user=self.other_user).update(
            date_last_filled=now() - timedelta(days=1)
        )
        cleanup_stacks.delay()
        self.assertEqual(self.stack.posts.count(), 0)

    def test_cleanup_stacks_to_soon(self):
        self.assertEqual(self.stack.posts.count(), 1)
        cleanup_stacks.delay()
        self.assertEqual(self.stack.posts.count(), 1)
