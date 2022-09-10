from posts.models import Comment
from posts.tests import PublishedPostTestCase

from .models import Notification


class Comment_create(PublishedPostTestCase):
    def test(self):
        Comment.objects.create(post=self.post, author=self.other_user, text="Text")
        notifications = Notification.objects.filter(subscription__user=self.other_user)
        self.assertEqual(notifications.count(), 0)
        notifications = Notification.objects.filter(subscription__user=self.main_user)
        self.assertEqual(notifications.count(), 1)
        notification = notifications.first()
        self.assertEqual(notification.subscription.user, self.main_user)
        self.assertEqual(notification.subscription.post, self.post)
        self.assertEqual(notification.count, 1)

    def test_same_author(self):
        Comment.objects.create(post=self.post, author=self.main_user, text="Text")
        notifications = Notification.objects.filter(subscription__user=self.main_user)
        self.assertEqual(notifications.count(), 0)


class Notification_save(PublishedPostTestCase):
    def test(self):
        Comment.objects.create(post=self.post, author=self.other_user, text="Text")
        notifications = Notification.objects.filter(subscription__user=self.main_user)
        self.assertEqual(notifications.count(), 1)
        notification = notifications.first()
        self.assertEqual(notification.count, 1)
        Comment.objects.create(post=self.post, author=self.other_user, text="Text")
        notification.refresh_from_db()
        self.assertEqual(notification.count, 2)
