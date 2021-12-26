from typing import Iterable

import grpc
from django.db.models import QuerySet

from core.pagination import PaginationAdapter
from protos import pagination_pb2, post_pb2

from .models import Post


class PostsPaginationAdapter(PaginationAdapter):
    def __init__(self, query: QuerySet, context: grpc.ServicerContext):
        super().__init__(query)
        self.context = context

    def make_message(self, item: Post, **overrides) -> post_pb2.Post:
        message: post_pb2.Post = super().make_message(item, **overrides)
        message.is_subscribed = item.subscribers.filter(
            id=self.context.caller.id
        ).exists()
        return message


class AnonymousPostsPaginationAdapter(PostsPaginationAdapter):
    def make_message(self, item: Post, **overrides) -> post_pb2.Post:
        if item.is_anonymous:
            overrides["author"] = None

        return super().make_message(item, **overrides)


class CreationDatePaginationAdapter(PaginationAdapter):
    def get_cursor_fields(self) -> Iterable[str]:
        return ["date_created", "id"]


class PublicationDatePaginationAdapter(PaginationAdapter):
    def get_cursor_fields(self) -> Iterable[str]:
        return ["date_published", "id"]


class ArchivePaginationAdapter(
    AnonymousPostsPaginationAdapter, PublicationDatePaginationAdapter
):
    pass


class OwnPostsPaginationAdapter(
    PostsPaginationAdapter, PublicationDatePaginationAdapter
):
    pass


class DraftsPaginationAdapter(PostsPaginationAdapter, CreationDatePaginationAdapter):
    pass


class CommentsPaginationAdapter(CreationDatePaginationAdapter):
    @property
    def random_access(self) -> bool:
        return True

    def __init__(self, query: QuerySet, context: grpc.ServicerContext):
        super().__init__(query)
        self.context = context

    def apply_header(self, header: pagination_pb2.Header):
        super().apply_header(header)
        post = Post.existing_objects.get_published_readable_by(
            self.context.caller, id=header.context_id
        )
        self.query = self.initial_query.filter(post=post)
