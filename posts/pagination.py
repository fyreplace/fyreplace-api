from typing import Iterable

from core.pagination import PaginationAdapter
from protos import pagination_pb2, post_pb2

from .models import Post, Subscription


class PostsPaginationAdapter(PaginationAdapter):
    def make_message(self, item: Post, **overrides) -> post_pb2.Post:
        return super().make_message(
            item, **overrides, **item.overrides_for_user(self.context.caller)
        )


class CreationDatePaginationAdapter(PaginationAdapter):
    def get_cursor_fields(self) -> Iterable[str]:
        return ["date_created", "id"]


class PublicationDatePaginationAdapter(PaginationAdapter):
    def get_cursor_fields(self) -> Iterable[str]:
        return ["date_published", "id"]


class ArchiveSubscriptionsPaginationAdapter(PaginationAdapter):
    def get_cursor_fields(self) -> Iterable[str]:
        return ["date_last_seen", "post_id"]

    def make_message(self, item: Subscription, **overrides) -> post_pb2.Post:
        return super().make_message(
            item.post, **overrides, **item.post.overrides_for_user(self.context.caller)
        )


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

    def apply_header(self, header: pagination_pb2.Header):
        super().apply_header(header)
        post = Post.existing_objects.get_published_readable_by(
            self.context.caller, id__bytes=header.context_id
        )
        self.query = self.initial_query.filter(post=post)
