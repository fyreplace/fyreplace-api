from django.core.exceptions import ObjectDoesNotExist, ValidationError
from rest_framework import exceptions, views
from rest_framework.response import Response


def exception_handler(exception: Exception, context: dict) -> Response:
    if isinstance(exception, ValidationError):
        exception = exceptions.ValidationError(exception.messages)
    elif isinstance(exception, ObjectDoesNotExist):
        exception = exceptions.NotFound()

    return views.exception_handler(exception, context)
