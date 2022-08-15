from typing import Iterator, List
from uuid import UUID

from django.utils.timezone import now

from core.tests import PaginationTestCase
from notifications.pagination import NotificationPaginationAdapter
from posts.models import Comment
from posts.test_services import CommentServiceTestCase, PostServiceTestCase
from protos import notification_pb2, pagination_pb2

from .models import Flag, Notification
from .services import NotificationService


class NotificationServiceTestCase(PostServiceTestCase, CommentServiceTestCase):
    def setUp(self):
        super().setUp()
        self.main_posts = self._create_posts(
            author=self.main_user, count=3, published=True, anonymous=False
        )
        self.other_posts = self._create_posts(
            author=self.other_user, count=3, published=True, anonymous=False
        )
        self.service = NotificationService()


class NotificationService_Count(NotificationServiceTestCase):
    def test(self):
        for i, post in enumerate(self.main_posts):
            for _ in range(i + 1):
                Comment.objects.create(author=self.other_user, post=post, text="Text")

        count = self.service.Count(self.request, self.grpc_context)
        self.assertEqual(count.count, Comment.objects.count())

    def test_no_subscription(self):
        Comment.objects.create(
            author=self.other_user, post=self.other_posts[0], text="Text"
        )
        count = self.service.Count(self.request, self.grpc_context)
        self.assertEqual(count.count, 0)

    def test_self_comment(self):
        Comment.objects.create(
            author=self.main_user, post=self.main_posts[0], text="Text"
        )
        count = self.service.Count(self.request, self.grpc_context)
        self.assertEqual(count.count, 0)

    def test_flags(self):
        self.main_user.is_staff = True
        self.main_user.save()

        for post in self.other_posts:
            Flag.objects.create(issuer=self.main_user, target=post)

        count = self.service.Count(self.request, self.grpc_context)
        self.assertEqual(count.count, len(self.other_posts))

    def test_flags_not_staff(self):
        for post in self.other_posts:
            Flag.objects.create(issuer=self.main_user, target=post)

        count = self.service.Count(self.request, self.grpc_context)
        self.assertEqual(count.count, 0)


class NotificationService_List(NotificationServiceTestCase, PaginationTestCase):
    main_pagination_field = "date_updated"

    def setUp(self):
        super().setUp()
        self.notifications = self._create_test_notifications()
        self.out_of_bounds_cursor = pagination_pb2.Cursor(
            data=[
                pagination_pb2.KeyValuePair(
                    key=self.main_pagination_field, value=str(now())
                ),
                pagination_pb2.KeyValuePair(
                    key="id", value=str(self.notifications[-1].id)
                ),
            ],
            is_next=True,
        )

    def paginate(self, request_iterator: Iterator[pagination_pb2.Page]) -> Iterator:
        return self.service.List(request_iterator, self.grpc_context)

    def check(self, item: notification_pb2.Notification, position: int):
        self.assertEqual(
            item.post.id, UUID(self.notifications[position].target_id).bytes
        )
        self.assertEqual(item.count, self.notifications[position].count)

    def test(self):
        self.run_test(self.check)

    def test_previous(self):
        self.run_test_previous(self.check)

    def test_reverse(self):
        self.run_test_reverse(self.check)

    def test_reverse_previous(self):
        self.run_test_reverse_previous(self.check)

    def test_empty(self):
        self.run_test_empty(self.post.comments.all())

    def test_invalid_size(self):
        self.run_test_invalid_size()

    def test_no_header(self):
        self.run_test_no_header()

    def test_out_of_bounds(self):
        self.run_test_out_of_bounds(self.out_of_bounds_cursor)

    def _create_test_notifications(self) -> List[Notification]:
        self._create_comments(author=self.other_user, count=14)
        self._create_comments(author=self.main_user, count=4)

        posts = self._create_posts(author=self.main_user, count=14, published=True)
        posts += self._create_posts(author=self.other_user, count=4, published=True)
        posts += self._create_posts(author=self.main_user, count=10, published=True)

        for i, post in enumerate(posts):
            for _ in range(i + 1):
                Comment.objects.create(post=post, author=self.other_user, text="Text")

        notifications = Notification.objects.filter(subscription__user=self.main_user)
        adapter = NotificationPaginationAdapter(self.grpc_context, notifications)
        return list(notifications.order_by(*adapter.get_cursor_fields()))


class NotificationService_Clear(NotificationServiceTestCase):
    def test(self):
        for i in range(len(self.main_posts)):
            for _ in range(i + 1):
                Comment.objects.create(
                    author=self.other_user, post=self.main_posts[i], text="Text"
                )

        self.service.Clear(self.request, self.grpc_context)
        self.assertEqual(
            Notification.objects.filter(subscription__user=self.main_user).count(),
            0,
        )
