from rest_framework import status
from rest_framework.reverse import reverse

from posts.models import Comment
from posts.tests import PostCreateMixin
from users.tests import AuthenticatedTestCase

from .models import Importance, Notification


class NotificationTestCase(PostCreateMixin, AuthenticatedTestCase):
    def setUp(self):
        super().setUp()
        self.main_post = self._create_published_post(author=self.main_user)
        self.other_post = self._create_published_post(author=self.other_user)

    def test_list(self):
        Comment.objects.create(post=self.main_post, author=self.main_user, text="Text")
        Comment.objects.create(post=self.main_post, author=self.other_user, text="Text")
        Comment.objects.create(post=self.other_post, author=self.main_user, text="Text")
        notification = Notification.objects.filter(user=self.main_user).first()
        url = reverse("notifications:notification-list")
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["count"], 1)
        chunk = self.main_post.chunks.first()
        self.assertEqual(
            response.data["results"],
            [
                {
                    "id": notification.id,
                    "post": {
                        "id": str(self.main_post.id),
                        "author": {
                            "id": str(self.main_user.id),
                            "username": self.main_user.username,
                        },
                        "date_created": self.main_post.date_published,
                        "chunks": [{"id": chunk.id, "text": chunk.text}],
                    },
                    "comments_count": 1,
                    "importance": Importance.COMMENT_OWN_POST.value,
                }
            ],
        )

    def test_list_subscribed(self):
        self.other_post.subscribers.add(self.main_user)
        Comment.objects.create(
            post=self.other_post, author=self.other_user, text="Text"
        )
        notification = Notification.objects.filter(user=self.main_user).first()
        url = reverse("notifications:notification-list")
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        chunk = self.other_post.chunks.first()
        self.assertEqual(
            response.data["results"],
            [
                {
                    "id": notification.id,
                    "post": {
                        "id": str(self.other_post.id),
                        "author": {
                            "id": str(self.other_user.id),
                            "username": self.other_user.username,
                        },
                        "date_created": self.other_post.date_published,
                        "chunks": [{"id": chunk.id, "text": chunk.text}],
                    },
                    "comments_count": 1,
                }
            ],
        )

    def test_clear(self):
        Comment.objects.create(post=self.main_post, author=self.other_user, text="New")
        self.assertEqual(Notification.objects.filter(user=self.main_user).count(), 1)
        url = reverse("notifications:notification-list")
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertEqual(Notification.objects.filter(user=self.main_user).count(), 0)

    def test_count(self):
        Comment.objects.create(post=self.main_post, author=self.other_user, text="New")
        url = reverse("notifications:notification-count")
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data, {"count": 1})

    def test_new(self):
        comment = Comment.objects.create(
            post=self.main_post, author=self.other_user, text="New"
        )
        notification = Notification.objects.filter(user=self.main_user).first()
        url = reverse("notifications:notification-new")
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        chunk = self.main_post.chunks.first()
        self.assertEqual(
            response.data["results"],
            [
                {
                    "id": notification.id,
                    "post": {
                        "id": str(self.main_post.id),
                        "author": {
                            "id": str(self.main_user.id),
                            "username": self.main_user.username,
                        },
                        "date_created": self.main_post.date_published,
                        "chunks": [{"id": chunk.id, "text": chunk.text}],
                    },
                    "comments_count": 1,
                    "latest_comment": {
                        "id": str(comment.id),
                        "date_created": comment.date_created,
                        "author": {
                            "id": str(self.other_user.id),
                            "username": self.other_user.username,
                        },
                        "text": comment.text,
                    },
                    "importance": Importance.COMMENT_OWN_POST.value,
                }
            ],
        )
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["results"], [])
