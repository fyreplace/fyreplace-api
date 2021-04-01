import jwt as j
from django.conf import settings
from jwt import *


def encode(payload: dict) -> str:
    return j.encode(
        payload=payload,
        key=settings.SECRET_KEY,
        algorithm=settings.JWT_ALGORITHM,
    )


def decode(token: str) -> dict:
    return j.decode(
        jwt=token,
        key=settings.SECRET_KEY,
        algorithms=[settings.JWT_ALGORITHM],
    )
