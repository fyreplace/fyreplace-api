from typing import Iterable

from core.pagination import PaginationAdapter


class UsersPaginationAdapter(PaginationAdapter):
    def get_cursor_fields(self) -> Iterable[str]:
        return ["username", "id"]
