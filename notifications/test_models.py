from posts.models import Comment
from posts.tests import BaseCommentTestCase, PublishedPostTestCase

from .models import Notification


class CommentTestCase(PublishedPostTestCase):
    def test_create(self):
        comment = Comment.objects.create(
            post=self.post, author=self.other_user, text="Text"
        )
        notifications = Notification.objects.filter(user=self.other_user)
        self.assertEqual(notifications.count(), 0)
        notifications = Notification.objects.filter(user=self.main_user)
        self.assertEqual(notifications.count(), 1)
        notification = notifications.first()
        self.assertEqual(notification.user, self.main_user)
        self.assertEqual(notification.post, self.post)
        self.assertEqual(notification.comments.count(), 1)
        self.assertEqual(notification.comments.first(), comment)

    def test_create_same_author(self):
        Comment.objects.create(post=self.post, author=self.main_user, text="Text")
        notifications = Notification.objects.filter(user=self.main_user)
        self.assertEqual(notifications.count(), 0)


class NotificationTestCase(BaseCommentTestCase):
    def test_delete(self):
        notification = Notification.objects.filter(user=self.main_user).first()
        self.assertEqual(notification.comments.count(), 1)
        notification.comments.clear()
        self.assertEqual(Notification.objects.filter(user=self.main_user).count(), 0)
