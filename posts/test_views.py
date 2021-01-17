import json
from datetime import datetime
from time import sleep

from django.utils.timezone import now
from rest_framework import status
from rest_framework.reverse import reverse

from core.permissions import CurrentUserIsOwnerOrReadOnly
from core.tests import get_asset
from users.models import Token
from users.tests import AuthenticatedTestCase

from .models import Chunk, Comment, Post
from .permissions import (
    CurrentUserCanVote,
    CurrentUserIsDraftOwner,
    CurrentUserIsNotBlocked,
    CurrentUserIsParentPostOwner,
    ParentPostIsDraft,
    ParentPostIsPublished,
    PostIsDraft,
    PostIsPublished,
    PostIsValid,
)
from .tests import BasePostTestCase, PostCreateMixin, PublishedPostTestCase


class PostTestCase(AuthenticatedTestCase, PostCreateMixin):
    def test_list(self):
        post = self._create_published_post(author=self.main_user)
        self._create_published_post(author=self.other_user)
        chunk = post.chunks.first()
        url = reverse("posts:post-list")
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(
            response.data["results"],
            [
                {
                    "id": str(post.id),
                    "date_created": post.date_published,
                    "author": {
                        "id": str(self.main_user.id),
                        "username": self.main_user.username,
                    },
                    "chunks": [{"id": chunk.id, "text": chunk.text}],
                }
            ],
        )

    def test_list_drafts(self):
        post = Post.objects.create(author=self.main_user)
        Post.objects.create(author=self.other_user)
        url = reverse("posts:post-list")
        response = self.client.get(url, {"published": False})
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(
            response.data["results"],
            [
                {
                    "id": str(post.id),
                    "date_created": post.date_created,
                    "author": {
                        "id": str(self.main_user.id),
                        "username": self.main_user.username,
                    },
                    "chunks": [],
                }
            ],
        )

    def test_retrieve_draft(self):
        post = Post.objects.create(author=self.main_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(
            response.data,
            {
                "id": str(post.id),
                "date_created": post.date_created,
                "author": {
                    "id": str(self.main_user.id),
                    "username": self.main_user.username,
                },
                "chunks": [],
            },
        )

    def test_retrieve_published(self):
        post = self._create_published_post(author=self.main_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(
            response.data,
            {
                "id": str(post.id),
                "date_created": post.date_published,
                "author": {
                    "id": str(self.main_user.id),
                    "username": self.main_user.username,
                },
                "chunks": [
                    {"id": chunk.id, "text": chunk.text}
                    for i, chunk in enumerate(post.chunks.all())
                ],
                "is_subscribed": True,
            },
        )

    def test_retrieve_other_draft(self):
        post = Post.objects.create(author=self.other_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data, CurrentUserIsDraftOwner.message)

    def test_retrieve_other_published(self):
        post = self._create_published_post(author=self.other_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(
            response.data,
            {
                "id": str(post.id),
                "date_created": post.date_published,
                "author": {
                    "id": str(self.other_user.id),
                    "username": self.other_user.username,
                },
                "chunks": [
                    {"id": chunk.id, "text": chunk.text}
                    for i, chunk in enumerate(post.chunks.all())
                ],
            },
        )

    def test_retrieve_other_published_anonymously(self):
        post = self._create_published_post(author=self.other_user, anonymous=True)
        url = reverse("posts:post-detail", args=[str(post.id)])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(
            response.data,
            {
                "id": str(post.id),
                "date_created": post.date_published,
                "chunks": [
                    {"id": chunk.id, "text": chunk.text}
                    for i, chunk in enumerate(post.chunks.all())
                ],
            },
        )

    def test_retrieve_deleted(self):
        post = self._create_published_post(author=self.main_user)
        post.delete()
        url = reverse("posts:post-detail", args=[str(post.id)])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_410_GONE)

    def test_create(self):
        url = reverse("posts:post-list")
        response = self.client.post(url)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn("id", response.data)
        self.assertIsInstance(response.data["id"], str)
        self.assertIn("date_created", response.data)
        self.assertIsInstance(response.data["date_created"], datetime)
        self.assertIn("chunks", response.data)
        self.assertEqual(response.data["chunks"], [])
        self.assertIn("author", response.data)
        self.assertEqual(
            response.data["author"],
            {"id": str(self.main_user.id), "username": self.main_user.username},
        )

    def test_update_chunk(self):
        post = self._create_draft(author=self.main_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        chunks = [{"id": chunk.id} for chunk in post.chunks.all()]
        chunks[0]["text"] = "New text"
        data = json.dumps({"chunks": chunks})
        response = self.client.patch(url, data, content_type="application/json")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data["chunks"]), len(chunks))
        self.assertEqual(response.data["chunks"][0], chunks[0])

    def test_update_chunk_position(self):
        post = self._create_draft(author=self.main_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        chunks = [{"id": chunk.id} for chunk in post.chunks.order_by("-position")]
        data = json.dumps({"chunks": chunks})
        response = self.client.patch(url, data, content_type="application/json")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data["chunks"]), len(chunks))

        for i in range(len(chunks)):
            self.assertEqual(response.data["chunks"][i]["id"], chunks[i]["id"])

    def test_update_add_chunk(self):
        post = Post.objects.create(author=self.main_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        data = json.dumps({"chunks": [{"text": "Text"}]})
        response = self.client.patch(url, data, content_type="application/json")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        chunks = response.data["chunks"]
        self.assertEqual(len(chunks), 1)
        self.assertIn("id", chunks[0])
        self.assertEqual(chunks[0]["text"], "Text")

    def test_udpate_delete_chunk(self):
        post = self._create_draft(author=self.main_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        chunks = [{"id": chunk.id} for chunk in post.chunks.all()]
        del chunks[1]
        data = json.dumps({"chunks": chunks})
        response = self.client.patch(url, data, content_type="application/json")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data["chunks"]), len(chunks))

        for i in range(len(chunks)):
            self.assertEqual(response.data["chunks"][i]["id"], chunks[i]["id"])

    def test_udpate_everything(self):
        post = self._create_draft(author=self.main_user, count=5)
        post_chunks = post.chunks.all()
        url = reverse("posts:post-detail", args=[str(post.id)])
        chunks = [
            {"id": post_chunks[0].id, "text": "New text"},
            {"text": "New chunk"},
            {"id": post_chunks[2].id},
            {"id": post_chunks[1].id},
        ]
        data = json.dumps({"chunks": chunks})
        response = self.client.patch(url, data, content_type="application/json")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        result_chunks = response.data["chunks"]
        self.assertEqual(len(result_chunks), len(chunks))
        self.assertEqual(result_chunks[0], chunks[0])
        self.assertEqual(result_chunks[1]["text"], chunks[1]["text"])

        for i in range(2, 3):
            self.assertEqual(result_chunks[i]["id"], chunks[i]["id"])

    def test_update_published(self):
        post = self._create_published_post(author=self.main_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        response = self.client.patch(url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data, PostIsDraft.message)

    def test_destroy(self):
        post = self._create_published_post(author=self.main_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertEqual(Post.objects.count(), 1)
        self.assertEqual(Post.alive_objects.count(), 0)

    def test_destroy_draft(self):
        post = Post.objects.create(author=self.main_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertEqual(Post.objects.count(), 0)

    def test_destroy_other(self):
        post = self._create_published_post(author=self.other_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data, CurrentUserIsOwnerOrReadOnly.message)

    def test_destroy_other_draft(self):
        post = Post.objects.create(author=self.other_user)
        url = reverse("posts:post-detail", args=[str(post.id)])
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data, CurrentUserIsDraftOwner.message)

    def test_subscribed(self):
        self._create_published_post(author=self.other_user)
        post = self._create_published_post(author=self.other_user)
        post.subscribers.add(self.main_user)
        chunk = post.chunks.first()
        url = reverse("posts:post-subscribed")
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data["results"]), 1)
        self.assertEqual(
            response.data["results"],
            [
                {
                    "id": str(post.id),
                    "date_created": post.date_published,
                    "author": {
                        "id": str(self.other_user.id),
                        "username": self.other_user.username,
                    },
                    "chunks": [{"id": chunk.id, "text": chunk.text}],
                }
            ],
        )

    def test_feed(self):
        Post.objects.create(author=self.other_user)
        self._create_published_post(author=self.main_user)
        post = self._create_published_post(author=self.other_user)
        before = now()
        sleep(0.1)
        url = reverse("posts:post-feed")
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(
            response.data,
            [
                {
                    "id": str(post.id),
                    "date_created": post.date_published,
                    "author": {
                        "id": str(self.other_user.id),
                        "username": self.other_user.username,
                    },
                    "chunks": [
                        {"id": chunk.id, "text": chunk.text}
                        for i, chunk in enumerate(post.chunks.all())
                    ],
                }
            ],
        )
        token = Token.objects.get(user=self.main_user)
        self.assertGreater(token.date_last_used, before)

    def test_feed_blocked_other(self):
        self.main_user.blocked_users.add(self.other_user)
        self._create_published_post(author=self.other_user)
        url = reverse("posts:post-feed")
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data, [])

    def test_publish(self):
        post = self._create_draft(author=self.main_user)
        url = reverse("posts:post-publish", args=[str(post.id)])
        response = self.client.post(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_publish_empty(self):
        post = Post.objects.create(author=self.main_user)
        url = reverse("posts:post-publish", args=[str(post.id)])
        response = self.client.post(url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data, PostIsValid.message)


class PostInteractionTestCase(PostCreateMixin, AuthenticatedTestCase):
    def setUp(self):
        super().setUp()
        self.post = self._create_published_post(author=self.other_user)
        self.main_user.stack.fill()
        self.post.refresh_from_db()
        self.post_life = self.post.life
        self.main_stack_count = self.main_user.stack.posts.count()

    def test_vote_spread(self):
        url = reverse("posts:post-vote", args=[str(self.post.id)])
        data = {"spread": True}
        response = self.client.post(url, data)
        self.post.refresh_from_db()
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(self.post.life, self.post_life + 4)
        self.assertEqual(self.main_user.stack.posts.count(), self.main_stack_count - 1)

    def test_vote_no_spread(self):
        url = reverse("posts:post-vote", args=[str(self.post.id)])
        data = {"spread": False}
        response = self.client.post(url, data)
        self.post.refresh_from_db()
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(self.post.life, self.post_life)
        self.assertEqual(self.main_user.stack.posts.count(), self.main_stack_count - 1)

    def test_vote_outside_stack(self):
        post = self._create_published_post(author=self.other_user)
        url = reverse("posts:post-vote", args=[str(post.id)])
        data = {"spread": True}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data, CurrentUserCanVote.message_post_outside_stack)

    def test_subscription(self):
        self.assertNotIn(self.main_user, self.post.subscribers.all())
        url = reverse("posts:post-subscription", args=[str(self.post.id)])
        response = self.client.post(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn(self.main_user, self.post.subscribers.all())

    def test_subscription_destroy(self):
        self.post.subscribers.add(self.main_user)
        url = reverse("posts:post-subscription", args=[str(self.post.id)])
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertNotIn(self.main_user, self.post.subscribers.all())

    def test_subscription_draft(self):
        post = self._create_draft(author=self.main_user)
        url = reverse("posts:post-subscription", args=[str(post.id)])
        response = self.client.post(url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data, PostIsPublished.message)
        self.assertNotIn(self.main_user, self.post.subscribers.all())

    def test_subscription_deleted(self):
        self.post.delete()
        url = reverse("posts:post-subscription", args=[str(self.post.id)])
        response = self.client.post(url)
        self.assertEqual(response.status_code, status.HTTP_410_GONE)
        self.assertNotIn(self.main_user, self.post.subscribers.all())


class ChunkTestCase(AuthenticatedTestCase, BasePostTestCase):
    def test_create(self):
        url = reverse("posts:post-chunk-list", args=[str(self.post.id)])
        data = {"text": "Text"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(self.post.chunks.count(), 1)
        self.assertIn("id", response.data)
        self.assertIn("position", response.data)
        self.assertEqual(response.data["position"], 0)
        self.assertIn("text", response.data)
        self.assertEqual(response.data["text"], data["text"])
        self.assertNotIn("image", response.data)

    def test_create_not_first(self):
        self._create_chunks(1)
        url = reverse("posts:post-chunk-list", args=[str(self.post.id)])
        data = {"text": "Second"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(self.post.chunks.count(), 2)
        self.assertIn("id", response.data)
        self.assertIn("position", response.data)
        self.assertEqual(response.data["position"], 1)
        self.assertIn("text", response.data)
        self.assertEqual(response.data["text"], data["text"])
        self.assertNotIn("image", response.data)

    def test_create_between(self):
        self._create_chunks(2)
        url = reverse("posts:post-chunk-list", args=[str(self.post.id)])
        data = {"position": 1, "text": "Middle"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(self.post.chunks.count(), 3)
        self.assertIn("id", response.data)
        self.assertIn("position", response.data)
        self.assertEqual(response.data["position"], 1)
        self.assertIn("text", response.data)
        self.assertEqual(response.data["text"], data["text"])
        self.assertNotIn("image", response.data)
        self.assertEqual(self.post.chunks.first().position, 0)
        self.assertEqual(self.post.chunks.last().position, 2)

    def test_create_image(self):
        url = reverse("posts:post-chunk-list", args=[str(self.post.id)])

        with open(get_asset("image.png"), "rb") as image:
            data = {"image": image}
            response = self.client.post(url, data)

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(self.post.chunks.count(), 1)
        self.assertIn("id", response.data)
        self.assertIn("position", response.data)
        self.assertEqual(response.data["position"], 0)
        self.assertIn("image", response.data)
        self.assertRegex(response.data["image"], r".*\.png")
        self.assertEqual(response.data["width"], 256)
        self.assertEqual(response.data["height"], 256)
        self.assertNotIn("text", response.data)

    def test_create_empty_text(self):
        url = reverse("posts:post-chunk-list", args=[str(self.post.id)])
        data = {"text": ""}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

    def test_create_no_content(self):
        url = reverse("posts:post-chunk-list", args=[str(self.post.id)])
        data = {}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_multiple_content_types(self):
        url = reverse("posts:post-chunk-list", args=[str(self.post.id)])

        with open(get_asset("image.png"), "rb") as image:
            data = {"text": "Text", "image": image}
            response = self.client.post(url, data)

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_too_many(self):
        for i in range(Post.MAX_CHUNKS):
            Chunk.objects.create(post=self.post, position=i, text=f"Text {i}")
        url = reverse("posts:post-chunk-list", args=[str(self.post.id)])
        data = {"text": "Text"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_in_published_post(self):
        self._create_chunks(1)
        self.post.publish(anonymous=False)
        url = reverse("posts:post-chunk-list", args=[str(self.post.id)])
        data = {"text": "Text"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data, ParentPostIsDraft.message)

    def test_create_in_other_post(self):
        post = Post.objects.create(author=self.other_user)
        url = reverse("posts:post-chunk-list", args=[str(post.id)])
        data = {"text": "Text"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data, CurrentUserIsParentPostOwner.message)

    def test_update(self):
        chunks = self._create_chunks(1)
        url = reverse(
            "posts:post-chunk-detail", args=[str(self.post.id), str(chunks[0].id)]
        )
        data = {"text": "New text"}
        response = self.client.patch(url, data)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["text"], data["text"])

    def test_update_change_content_type(self):
        chunks = self._create_chunks(1)
        url = reverse(
            "posts:post-chunk-detail", args=[str(self.post.id), str(chunks[0].id)]
        )

        with open(get_asset("image.png"), "rb") as image:
            data = {"image": image}
            response = self.client.patch(url, data)

        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_update_position(self):
        chunks = self._create_chunks(2)
        url = reverse(
            "posts:post-chunk-detail", args=[str(self.post.id), str(chunks[0].id)]
        )
        data = {"position": 1}
        response = self.client.patch(url, data)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(self.post.chunks.first().id, chunks[1].id)

    def test_update_position_and_content(self):
        chunks = self._create_chunks(2)
        url = reverse(
            "posts:post-chunk-detail", args=[str(self.post.id), str(chunks[0].id)]
        )
        data = {"position": 1, "text": "New text"}
        response = self.client.patch(url, data)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(self.post.chunks.first().id, chunks[1].id)
        self.assertEqual(self.post.chunks.last().text, data["text"])

    def test_destroy(self):
        chunks = self._create_chunks(2)
        url = reverse(
            "posts:post-chunk-detail", args=[str(self.post.id), str(chunks[0].id)]
        )
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)
        self.assertEqual(self.post.chunks.count(), 1)
        self.assertEqual(self.post.chunks.first().position, 0)

    def _create_chunks(self, count: int):
        return [
            Chunk.objects.create(post=self.post, position=i, text=f"Text {i}")
            for i in range(count)
        ]


class CommentTestCase(PostCreateMixin, AuthenticatedTestCase, PublishedPostTestCase):
    def test_list(self):
        comment = Comment.objects.create(
            author=self.other_user, post=self.post, text="Text"
        )
        url = reverse("posts:post-comment-list", args=[str(self.post.id)])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(
            response.data["results"],
            [
                {
                    "id": str(comment.id),
                    "date_created": comment.date_created,
                    "author": {
                        "id": str(self.other_user.id),
                        "username": self.other_user.username,
                    },
                    "text": comment.text,
                }
            ],
        )

    def test_list_blocked(self):
        self.main_user.blocked_users.add(self.other_user)
        comment = Comment.objects.create(
            author=self.other_user, post=self.post, text="Text"
        )
        url = reverse("posts:post-comment-list", args=[str(self.post.id)])
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(
            response.data["results"],
            [
                {
                    "id": str(comment.id),
                    "date_created": comment.date_created,
                    "is_blocked": True,
                }
            ],
        )

    def test_create(self):
        url = reverse("posts:post-comment-list", args=[str(self.post.id)])
        data = {"text": "Text"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data["text"], data["text"])

    def test_create_other(self):
        post = self._create_published_post(author=self.other_user)
        url = reverse("posts:post-comment-list", args=[str(post.id)])
        data = {"text": "Text"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data["text"], data["text"])

    def test_create_in_draft(self):
        post = Post.objects.create(author=self.main_user)
        url = reverse("posts:post-comment-list", args=[str(post.id)])
        data = {"text": "Text"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data, ParentPostIsPublished.message)

    def test_create_in_deleted(self):
        self.post.delete()
        url = reverse("posts:post-comment-list", args=[str(self.post.id)])
        data = {"text": "Text"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_410_GONE)

    def test_create_blocked(self):
        self.other_user.blocked_users.add(self.main_user)
        post = self._create_published_post(author=self.other_user)
        url = reverse("posts:post-comment-list", args=[str(post.id)])
        data = {"text": "Text"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data, CurrentUserIsNotBlocked.message)

    def test_create_blocked_in_anonymous(self):
        self.other_user.blocked_users.add(self.main_user)
        post = self._create_published_post(author=self.other_user, anonymous=True)
        url = reverse("posts:post-comment-list", args=[str(post.id)])
        data = {"text": "Text"}
        response = self.client.post(url, data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data["text"], data["text"])

    def test_destroy(self):
        comment = Comment.objects.create(
            author=self.main_user, post=self.post, text="Text"
        )
        url = reverse(
            "posts:post-comment-detail", args=[str(self.post.id), str(comment.id)]
        )
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_204_NO_CONTENT)

    def test_destroy_other(self):
        comment = Comment.objects.create(
            author=self.other_user, post=self.post, text="Text"
        )
        url = reverse(
            "posts:post-comment-detail", args=[str(self.post.id), str(comment.id)]
        )
        response = self.client.delete(url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(response.data, CurrentUserIsOwnerOrReadOnly.message)
