from typing import Any

from rest_framework.decorators import action
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.status import HTTP_200_OK

from flags.serializers import FlagSerializer


class FlagMixin:
    @action(methods=["POST"], detail=True)
    def flag(self, request: Request, pk: Any):
        serializer = FlagSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        serializer.save(target=self.get_object())
        return Response(status=HTTP_200_OK, data=serializer.data)
