from django.utils.translation import gettext_lazy as _
from rest_framework import status
from rest_framework.exceptions import APIException


class Conflict(APIException):
    status_code = status.HTTP_409_CONFLICT
    default_detail = _("Conflict.")
    default_code = "conflict"


class Gone(APIException):
    status_code = status.HTTP_410_GONE
    default_detail = _("Deleted.")
    default_code = "gone"


class PayloadTooLarge(APIException):
    status_code = status.HTTP_413_REQUEST_ENTITY_TOO_LARGE
    default_detail = _("Payload too large.")
    default_code = "payload_too_large"
