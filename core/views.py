from django.core.exceptions import ObjectDoesNotExist, ValidationError
from django.http.request import HttpRequest
from django.http.response import HttpResponse
from rest_framework import exceptions, views
from rest_framework.response import Response


def exception_handler(exception: Exception, context: dict) -> Response:
    if isinstance(exception, ValidationError):
        exception = exceptions.ValidationError(exception.messages)
    elif isinstance(exception, ObjectDoesNotExist):
        exception = exceptions.NotFound()

    return views.exception_handler(exception, context)


def robots_txt(request: HttpRequest) -> HttpResponse:
    return HttpResponse(
        content_type="text/plain",
        content="User-agent: *\nDisallow: /",
    )
