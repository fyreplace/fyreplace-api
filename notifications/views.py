from django.db.models import QuerySet
from rest_framework.decorators import action
from rest_framework.mixins import ListModelMixin
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.status import HTTP_200_OK
from rest_framework.viewsets import GenericViewSet

from core.mixins import ClearModelMixin
from core.signals import fetched
from notifications.pagination import NotificationPagination

from .models import Notification
from .serializers import NewNotificationSerializer, NotificationSerializer


class NotificationViewSet(ListModelMixin, ClearModelMixin, GenericViewSet):
    queryset = Notification.objects.all()
    serializer_class = NotificationSerializer
    pagination_class = NotificationPagination

    def get_queryset(self) -> QuerySet:
        return super().get_queryset().filter(user=self.request.user)

    @action(methods=["GET"], detail=False)
    def count(self, request: Request) -> Response:
        count = self.filter_queryset(self.get_queryset()).count()
        return Response(status=HTTP_200_OK, data={"count": count})

    @action(methods=["GET"], detail=False)
    def new(self, request: Request) -> Response:
        queryset = self.filter_queryset(self.get_queryset().filter(received=False))
        page = self.paginate_queryset(queryset)
        serializer = NewNotificationSerializer(
            instance=page, context=self.get_serializer_context(), many=True
        )
        response = self.get_paginated_response(serializer.data)
        response.data["previous"] = None
        response.data["next"] = None
        pk_set = list(queryset.values_list("id", flat=True))
        fetched.send(sender=self.queryset.model, user=request.user, pk_set=pk_set)
        return response
