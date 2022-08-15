from typing import Iterator

import grpc
from django.db.models import F, OuterRef, Sum
from google.protobuf import empty_pb2

from core.pagination import PaginatorMixin
from posts.models import Comment, Subscription
from protos import notification_pb2, notification_pb2_grpc, pagination_pb2

from .models import Notification
from .pagination import NotificationPaginationAdapter


class NotificationService(
    PaginatorMixin, notification_pb2_grpc.NotificationServiceServicer
):
    def Count(
        self, request: empty_pb2.Empty, context: grpc.ServicerContext
    ) -> notification_pb2.NotificationCount:
        count = (
            Notification.objects.filter_readable_by(context.caller)
            .aggregate(total_count=Sum("count"))
            .get("total_count")
        )
        return notification_pb2.NotificationCount(count=count)

    def List(
        self,
        request_iterator: Iterator[pagination_pb2.Page],
        context: grpc.ServicerContext,
    ) -> Iterator[notification_pb2.Notifications]:
        notifications = Notification.objects.filter_readable_by(context.caller)
        return self.paginate(
            request_iterator,
            bundle_class=notification_pb2.Notifications,
            adapter=NotificationPaginationAdapter(context, notifications),
            message_overrides={"is_preview": True},
        )

    def Clear(
        self, request: empty_pb2.Empty, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        Subscription.objects.filter(user=context.caller).annotate(
            last_comment_id=(
                Comment.objects.filter(post_id=OuterRef("post_id"))
                .order_by("-date_created", "-id")
                .values("id")[:1]
            )
        ).update(last_comment_seen_id=F("last_comment_id"))

        Notification.objects.filter(
            subscription__in=Subscription.objects.filter(user=context.caller)
        ).delete()

        return empty_pb2.Empty()
