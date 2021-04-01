from typing import Iterable

from core.pagination import PaginationAdapter


class CreationDatePaginationAdapter(PaginationAdapter):
    def get_cursor_fields(self) -> Iterable[str]:
        return ["date_created", "id"]


class PublicationDatePaginationAdapter(PaginationAdapter):
    def get_cursor_fields(self) -> Iterable[str]:
        return ["date_published", "id"]
