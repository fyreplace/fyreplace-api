from base64 import urlsafe_b64encode
from typing import Union
from uuid import UUID


def b64encode(data: Union[bytes, UUID], padding: bool = False) -> str:
    encoded_data = urlsafe_b64encode(
        data.bytes if isinstance(data, UUID) else data
    ).decode("ascii")
    return encoded_data if padding else encoded_data.replace("=", "")


def cut_text(text: str) -> str:
    return text[:255] + "\u2026" if len(text) > 256 else text
