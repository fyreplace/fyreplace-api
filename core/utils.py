from rest_framework.exceptions import ValidationError

from .exceptions import reason


def str_to_bool(string: str) -> bool:
    lowered = string.lower()

    if lowered == "true":
        return True
    elif lowered == "false":
        return False
    else:
        raise ValidationError(reason(["value_not_boolean"]))
