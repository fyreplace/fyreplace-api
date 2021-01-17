from typing import Any

from django.core.validators import BaseValidator

from .exceptions import PayloadTooLarge


class FileSizeValidator(BaseValidator):
    def __init__(self, max_megabytes: float):
        self.max_bytes = max_megabytes * 1024 * 1024

    def __call__(self, value: Any):
        if value.size > self.max_bytes:
            raise PayloadTooLarge
