from os import path
from typing import Any

from django.contrib.auth.validators import UnicodeUsernameValidator
from django.core.validators import BaseValidator
from rest_framework.exceptions import ValidationError

from core.utils import normalize

validate_unicode_username = UnicodeUsernameValidator()


class UsernameNotReservedValidator(BaseValidator):
    def __init__(self):
        with open(path.join(__package__, "reserved-usernames.txt"), "r") as reserved:
            self.reserved_usernames = [normalize(name) for name in reserved]

        super().__init__(None, "username_reserved")

    def __call__(self, value: Any):
        if value.upper() in self.reserved_usernames:
            raise ValidationError("reserved_username")
