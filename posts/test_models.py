from time import sleep

from django.core.files.images import ImageFile
from django.db import IntegrityError
from django.utils.timezone import now
from rest_framework.exceptions import ValidationError

from core.tests import get_asset

from .models import Chunk, Comment, Post, Stack, Vote
from .tests import BaseCommentTestCase, BasePostTestCase, PublishedPostTestCase


class UserTestCase(BaseCommentTestCase):
    def test_delete(self):
        self.main_user.delete()
        self.assertEqual(Post.objects.filter(is_deleted=False).count(), 0)
        self.assertEqual(Comment.objects.filter(is_deleted=False).count(), 0)


class PostTestCase(BasePostTestCase):
    def test_delete(self):
        self.post.delete()
        self.assertIsNone(self.post.id)

    def test_delete_published(self):
        Chunk.objects.create(post=self.post, position=0, text="Text")
        self.post.publish(anonymous=False)
        self.post.delete()
        self.assertIsNotNone(self.post.id)
        self.assertEqual(Post.alive_objects.count(), 0)
        self.assertEqual(self.post.chunks.count(), 0)
        self.assertEqual(self.post.life, 0)

    def test_publish(self):
        Chunk.objects.create(post=self.post, position=0, text="Text")
        self.post.publish(anonymous=False)
        self.assertIsNotNone(self.post.date_published)
        self.assertEqual(self.post.life, 10)
        self.assertFalse(self.post.is_anonymous)
        self.assertEqual(self.post.subscribers.count(), 1)
        self.assertEqual(self.post.subscribers.first(), self.main_user)

    def test_publish_anonymously(self):
        Chunk.objects.create(post=self.post, position=0, text="Text")
        self.post.publish(anonymous=True)
        self.assertTrue(self.post.is_anonymous)

    def test_publish_empty(self):
        with self.assertRaises(ValidationError):
            self.post.publish(anonymous=False)

    def test_publish_already_published(self):
        Chunk.objects.create(post=self.post, position=0, text="Text")
        self.post.publish(anonymous=False)

        with self.assertRaises(IntegrityError):
            self.post.publish(anonymous=False)

    def test_publish_invalid_chunk(self):
        Chunk.objects.create(post=self.post, position=0)

        with self.assertRaises(ValidationError):
            self.post.publish(anonymous=False)


class ChunkTestCase(BasePostTestCase):
    def test_validate_text(self):
        chunk = Chunk.objects.create(post=self.post, position=0, text="Text")
        self.assertValid(chunk)

    def test_validate_image(self):
        chunk = Chunk.objects.create(
            post=self.post,
            position=0,
            image=self._get_image_file(),
        )

        self.assertEqual(chunk.width, 256)
        self.assertEqual(chunk.height, 256)
        self.assertValid(chunk)

    def test_validate_empty(self):
        chunk = Chunk.objects.create(post=self.post, position=0)

        with self.assertRaises(ValidationError):
            chunk.validate()

    def test_validate_multiple_types(self):
        chunk = Chunk.objects.create(
            post=self.post,
            position=0,
            text="Text",
            image=self._get_image_file(),
        )

        with self.assertRaises(ValidationError):
            chunk.validate()

    def test_validate_text_empty(self):
        chunk = Chunk.objects.create(post=self.post, position=0, text="")

        with self.assertRaises(ValidationError):
            chunk.validate()

    def assertValid(self, chunk: Chunk):
        try:
            chunk.validate()
        except ValidationError as err:
            self.fail(err.detail)

    def _get_image_file(self):
        asset = open(get_asset("image.png"), "rb")
        return ImageFile(file=asset, name="image")


class CommentTestCase(BaseCommentTestCase):
    def test_create(self):
        self.assertEqual(self.post.comments.count(), 1)
        self.assertEqual(self.post.comments.first(), self.comment)
        self.assertEqual(self.post.subscribers.count(), 2)
        self.assertIn(self.main_user, self.post.subscribers.all())
        self.assertIn(self.other_user, self.post.subscribers.all())

    def test_delete(self):
        self.comment.delete()
        self.assertTrue(self.comment.is_deleted)
        self.assertEqual(self.comment.text, "")
        self.assertEqual(self.post.comments.count(), 1)


class StackTestCase(PublishedPostTestCase):
    def test_create(self):
        self.assertEqual(Stack.objects.filter(user=self.main_user).count(), 1)

    def test_fill(self):
        post_life = self.post.life
        before = now()
        sleep(0.1)
        stack = self.other_user.stack
        stack.fill()
        self.assertEqual(stack.posts.count(), 1)
        self.assertEqual(stack.posts.first().life, post_life - 1)
        self.assertGreater(stack.date_last_filled, before)

    def test_fill_again(self):
        stack = self.other_user.stack
        stack.fill()
        self.assertEqual(stack.posts.count(), 1)
        stack.fill()
        self.assertEqual(stack.posts.count(), 1)

    def test_fill_completely(self):
        for user in (self.main_user, self.other_user):
            for i in range(15):
                post = Post.objects.create(author=user)
                Chunk.objects.create(post=post, position=0, text="Text")
                post.publish(anonymous=False)

        stack = self.other_user.stack
        stack.fill()
        self.assertEqual(stack.posts.count(), 10)
        self.assertEqual(stack.posts.filter(author=self.other_user).count(), 0)

    def test_drain(self):
        post_life = self.post.life
        stack = self.other_user.stack
        stack.fill()
        stack.drain()
        self.assertEqual(stack.posts.count(), 0)
        self.assertEqual(self.post.life, post_life)


class VoteTestCase(PublishedPostTestCase):
    def test_create_spread(self):
        post_life = self.post.life
        self.other_user.stack.fill()
        stack_count = self.other_user.stack.posts.count()
        Vote.objects.create(user=self.other_user, post=self.post, spread=True)
        self.assertEqual(self.post.life, post_life + 4)
        self.assertEqual(self.other_user.stack.posts.count(), stack_count - 1)

    def test_create_no_spread(self):
        post_life = self.post.life
        self.other_user.stack.fill()
        stack_count = self.other_user.stack.posts.count()
        Vote.objects.create(user=self.other_user, post=self.post, spread=False)
        self.assertEqual(self.post.life, post_life)
        self.assertEqual(self.other_user.stack.posts.count(), stack_count - 1)

    def test_create_same_user(self):
        with self.assertRaises(IntegrityError):
            Vote.objects.create(user=self.main_user, post=self.post, spread=True)

    def test_create_exising_vote(self):
        Vote.objects.create(user=self.other_user, post=self.post, spread=True)

        with self.assertRaises(IntegrityError):
            Vote.objects.create(user=self.other_user, post=self.post, spread=True)
