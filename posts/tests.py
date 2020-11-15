from django.contrib.auth.models import AbstractUser
from django.db import DatabaseError

from users.tests import BaseUserTestCase

from .models import Chunk, Comment, Post


class PostCreateMixin:
    def _create_published_post(
        self, author: AbstractUser, anonymous: bool = False
    ) -> Post:
        post = self._create_draft(author)
        post.publish(anonymous=anonymous)
        return post

    def _create_draft(self, author: AbstractUser, count: int = 3) -> Post:
        post = Post.objects.create(author=author)

        for i in range(count):
            Chunk.objects.create(post=post, position=i, text=f"Text {i}")

        return post


class BasePostTestCase(BaseUserTestCase):
    def setUp(self):
        super().setUp()
        self.post = Post.objects.create(author=self.main_user)

    def tearDown(self):
        try:
            if self.post.id is not None:
                self.post.delete()
        except DatabaseError:
            pass


class PublishedPostTestCase(BasePostTestCase):
    def setUp(self):
        super().setUp()
        Chunk.objects.create(post=self.post, position=0, text="Text")
        self.post.publish(anonymous=False)


class BaseCommentTestCase(PublishedPostTestCase):
    def setUp(self):
        super().setUp()
        self.comment = Comment.objects.create(
            post=self.post, author=self.other_user, text="Text"
        )
