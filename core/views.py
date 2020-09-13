from django.conf import settings
from django.core.exceptions import ValidationError
from rest_framework import exceptions, views
from rest_framework.response import Response
from rest_framework.reverse import reverse


def exception_handler(exception: Exception, context: dict) -> Response:
    if isinstance(exception, ValidationError):
        exception = exceptions.ValidationError(exception.messages)

    return views.exception_handler(exception, context)


def deep_link(url_name: str, *args, **kwargs) -> str:
    return f"{settings.APP_NAME}://{reverse(url_name, args=args, kwargs=kwargs)}"
