from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.status import HTTP_204_NO_CONTENT


class ClearModelMixin:
    def clear(self, request: Request, *args, **kwargs) -> Response:
        instances = self.filter_queryset(self.get_queryset())
        self.perform_clear(instances)
        return Response(status=HTTP_204_NO_CONTENT)

    def perform_clear(self, instances):
        instances.delete()
