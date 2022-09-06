from typing import Any

from django.core.validators import BaseValidator
from grpc_interceptor.exceptions import InvalidArgument


class FileSizeValidator(BaseValidator):
    def __init__(self, max_bytes: int):
        super().__init__(max_bytes)

    def __call__(self, value: Any):
        if value.size > self.limit_value:
            raise InvalidArgument("payload_too_large")
