from posts.models import Comment
from posts.tests import BaseCommentTestCase, PublishedPostTestCase

from .models import CountUnit, Notification


class Comment_create(PublishedPostTestCase):
    def test(self):
        comment = Comment.objects.create(
            post=self.post, author=self.other_user, text="Text"
        )
        notifications = Notification.objects.filter(recipient=self.other_user)
        self.assertEqual(notifications.count(), 0)
        notifications = Notification.objects.filter(recipient=self.main_user)
        self.assertEqual(notifications.count(), 1)
        notification = notifications.first()
        self.assertEqual(notification.recipient, self.main_user)
        self.assertEqual(notification.target, self.post)
        self.assertEqual(notification.count, 1)
        self.assertEqual(CountUnit.objects.first().count_item, comment)

    def test_same_author(self):
        Comment.objects.create(post=self.post, author=self.main_user, text="Text")
        notifications = Notification.objects.filter(recipient=self.main_user)
        self.assertEqual(notifications.count(), 0)


class Notification_delete(BaseCommentTestCase):
    def test(self):
        notification = Notification.objects.filter(recipient=self.main_user).first()
        self.assertEqual(notification.count, 1)
        CountUnit.objects.filter(notification_id=notification.id).delete()
        self.assertEqual(
            Notification.objects.filter(recipient=self.main_user).count(), 0
        )
