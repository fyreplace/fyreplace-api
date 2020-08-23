import re

from django.db.models import QuerySet
from rest_framework import pagination
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.views import APIView


class LimitOffsetPagination(pagination.LimitOffsetPagination):
    max_limit = 50

    def get_paginated_response(self, data: dict) -> Response:
        response = super().get_paginated_response(data)

        for property in ("previous", "next"):
            del response.data[property]

        return response

    def get_paginated_response_schema(self, schema: dict):
        result = super().get_paginated_response_schema(schema)

        for property in ("previous", "next"):
            del result["properties"][property]

        return result


class CursorPagination(pagination.CursorPagination):
    page_size_query_param = "page_size"

    def __init__(self) -> None:
        super().__init__()
        self.cursor_regex = re.compile(f"(?<={self.cursor_query_param}=)[^&]+")

    def encode_cursor(self, cursor: pagination.Cursor) -> str:
        cursor_url = super().encode_cursor(cursor)
        return self.cursor_regex.search(cursor_url)[0]

    def get_ordering(
        self, request: Request, queryset: QuerySet, view: APIView
    ) -> tuple:
        self.ordering = queryset.model._meta.ordering
        return super().get_ordering(request, queryset, view)
