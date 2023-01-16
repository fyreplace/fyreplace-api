import re
import unicodedata
from uuid import UUID

from rest_framework.exceptions import ValidationError


def str_to_bool(string: str) -> bool:
    lowered = string.lower()

    if lowered == "true":
        return True
    elif lowered == "false":
        return False
    else:
        raise ValueError


def make_uuid(data: bytes) -> UUID:
    try:
        return UUID(bytes=data)
    except ValueError:
        raise ValidationError("invalid_uuid")


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore")
    return re.sub(r"[^\w]", "", value.decode("ascii")).strip().upper()
