from typing import Iterable

import grpc
from django.db.models import QuerySet

from core.pagination import PaginationAdapter
from protos import pagination_pb2

from .models import Post


class CreationDatePaginationAdapter(PaginationAdapter):
    def get_cursor_fields(self) -> Iterable[str]:
        return ["date_created", "id"]


class PublicationDatePaginationAdapter(PaginationAdapter):
    def get_cursor_fields(self) -> Iterable[str]:
        return ["date_published", "id"]


class CommentPaginationAdapter(CreationDatePaginationAdapter):
    def __init__(self, query: QuerySet, context: grpc.ServicerContext):
        super().__init__(query)
        self.context = context

    def apply_header(self, header: pagination_pb2.Header):
        super().apply_header(header)
        post = Post.existing_objects.get_published_readable_by(
            self.context.caller, id=header.context_id
        )
        self.query = self.initial_query.filter(post=post)
