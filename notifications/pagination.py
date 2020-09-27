from django.db.models.query import QuerySet
from rest_framework.request import Request
from rest_framework.response import Response

from core.pagination import CursorPagination


class NotificationPagination(CursorPagination):
    def paginate_queryset(self, queryset: QuerySet, request: Request, view):
        self.count = queryset.count()
        return super().paginate_queryset(queryset, request, view)

    def get_paginated_response(self, data: dict) -> Response:
        response = super().get_paginated_response(data)
        response.data["count"] = self.count
        return response

    def get_paginated_response_schema(self, schema: dict):
        result = super().get_paginated_response_schema(schema)
        result["properties"]["count"] = {"type": "integer"}
        return result
