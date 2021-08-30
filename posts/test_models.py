from time import sleep
from unittest.case import TestCase

from django.core.files.images import ImageFile
from django.db import IntegrityError
from django.utils.timezone import now
from grpc_interceptor.exceptions import InvalidArgument

from core.tests import get_asset

from .models import Chapter, Comment, Post, Vote, position_between
from .tests import BaseCommentTestCase, BasePostTestCase, PublishedPostTestCase


class PositionBetween(TestCase):
    def test(self):
        for data in [
            (None, None, "z"),
            ("z", None, "zz"),
            (None, "z", "az"),
            ("z", "zz", "zaz"),
            ("z", "zaz", "zaaz"),
            ("zaz", "zz", "zazz"),
            ("az", "z", "azz"),
            ("azz", "z", "azzz"),
            ("az", "azz", "azaz"),
        ]:
            self.assertEqual(position_between(data[0], data[1]), data[2])

    def test_invalid_argument_type(self):
        with self.assertRaises(TypeError):
            position_between(42, None)

    def test_same_argument(self):
        with self.assertRaises(RuntimeError):
            position_between("z", "z")

    def test_inverted_argument(self):
        with self.assertRaises(RuntimeError):
            position_between("z", "az")


class User_delete(BaseCommentTestCase):
    def test(self):
        self.main_user.delete()
        self.assertEqual(Post.objects.filter(is_deleted=False).count(), 0)
        self.assertEqual(Comment.objects.filter(is_deleted=False).count(), 0)


class Post_delete(BasePostTestCase):
    def setUp(self):
        super().setUp()
        Chapter.objects.create(
            post=self.post, position=self.post.chapter_position(0), text=f"Text"
        )
        self.post.publish(anonymous=False)
        self.other_user.stack.fill()

    def test(self):
        self.assertEqual(self.other_user.stack.posts.count(), 1)
        self.post.delete()
        self.assertEqual(self.other_user.stack.posts.count(), 0)


class Post_normalize_chapters(BasePostTestCase):
    def test(self):
        for i in range(Post.MAX_CHAPTERS):
            Chapter.objects.create(
                post=self.post,
                position=self.post.chapter_position(i),
                text=f"Text {i}",
            )

        chapters = list(self.post.chapters.all())
        chapters[3].position = self.post.chapter_position(6)
        chapters[5].position = self.post.chapter_position(1)
        chapters[9].position = self.post.chapter_position(4)
        chapters = list(self.post.chapters.all())
        self.post.normalize_chapters()

        positions = [
            "aaaaaz",
            "aaaaz",
            "aaaz",
            "aaz",
            "az",
            "z",
            "zz",
            "zzz",
            "zzzz",
            "zzzzz",
        ]

        for i, chapter in enumerate(self.post.chapters.all()):
            self.assertEqual(chapter.id, chapters[i].id)
            self.assertEqual(chapter.position, positions[i])


class Chapter_validate(BasePostTestCase):
    def test_text(self):
        chapter = Chapter.objects.create(
            post=self.post, position=self.post.chapter_position(0), text="Text"
        )
        self.assertValid(chapter)

    def test_image(self):
        chapter = Chapter.objects.create(
            post=self.post,
            position=self.post.chapter_position(0),
            image=self._get_image_file(),
        )

        self.assertEqual(chapter.width, 256)
        self.assertEqual(chapter.height, 256)
        self.assertValid(chapter)

    def test_empty(self):
        chapter = Chapter.objects.create(
            post=self.post, position=self.post.chapter_position(0)
        )

        with self.assertRaises(InvalidArgument):
            chapter.validate()

    def test_text_empty(self):
        chapter = Chapter.objects.create(
            post=self.post, position=self.post.chapter_position(0), text=""
        )

        with self.assertRaises(InvalidArgument):
            chapter.validate()

    def assertValid(self, chapter: Chapter):
        try:
            chapter.validate()
        except InvalidArgument as err:
            self.fail(err.detail)

    def _get_image_file(self):
        asset = open(get_asset("image.png"), "rb")
        return ImageFile(file=asset, name="image.png")


class Stack_fill(PublishedPostTestCase):
    def test(self):
        post_life = self.post.life
        before = now()
        sleep(0.1)
        stack = self.other_user.stack
        stack.fill()
        self.assertEqual(stack.posts.count(), 1)
        self.assertEqual(stack.posts.first().life, post_life - 1)
        self.assertGreater(stack.date_last_filled, before)

    def test_again(self):
        stack = self.other_user.stack
        stack.fill()
        self.assertEqual(stack.posts.count(), 1)
        stack.fill()
        self.assertEqual(stack.posts.count(), 1)

    def test_completely(self):
        for user in (self.main_user, self.other_user):
            for i in range(15):
                post = Post.objects.create(author=user)
                Chapter.objects.create(
                    post=post, position=self.post.chapter_position(0), text="Text"
                )
                post.publish(anonymous=False)

        stack = self.other_user.stack
        stack.fill()
        self.assertEqual(stack.posts.count(), 10)
        self.assertEqual(stack.posts.filter(author=self.other_user).count(), 0)


class Stack_drain(PublishedPostTestCase):
    def test(self):
        post_life = self.post.life
        stack = self.other_user.stack
        stack.fill()
        stack.drain()
        self.assertEqual(stack.posts.count(), 0)
        self.assertEqual(self.post.life, post_life)


class Vote_create(PublishedPostTestCase):
    def test_spread(self):
        self.other_user.stack.fill()
        self.post.refresh_from_db()
        post_life = self.post.life
        stack_count = self.other_user.stack.posts.count()
        Vote.objects.create(user=self.other_user, post=self.post, spread=True)
        self.post.refresh_from_db()
        self.assertEqual(self.post.life, post_life + 4)
        self.assertEqual(self.other_user.stack.posts.count(), stack_count - 1)

    def test_no_spread(self):
        self.other_user.stack.fill()
        self.post.refresh_from_db()
        post_life = self.post.life
        stack_count = self.other_user.stack.posts.count()
        Vote.objects.create(user=self.other_user, post=self.post, spread=False)
        self.post.refresh_from_db()
        self.assertEqual(self.post.life, post_life)
        self.assertEqual(self.other_user.stack.posts.count(), stack_count - 1)

    def test_same_user(self):
        with self.assertRaises(IntegrityError):
            Vote.objects.create(user=self.main_user, post=self.post, spread=True)

    def test_exising_vote(self):
        Vote.objects.create(user=self.other_user, post=self.post, spread=True)

        with self.assertRaises(IntegrityError):
            Vote.objects.create(user=self.other_user, post=self.post, spread=True)
