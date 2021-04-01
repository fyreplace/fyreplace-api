from typing import Iterable

from core.pagination import PaginationAdapter


class NotificationPaginationAdapter(PaginationAdapter):
    def get_cursor_fields(self) -> Iterable[str]:
        return ["importance", "count", "date_updated", "id"]
