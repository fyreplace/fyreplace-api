import jwt as j
from django.conf import settings
from jwt import *


def encode(payload: dict) -> str:
    return j.encode(
        payload=payload,
        key=settings.SECRET_KEY,
        algorithm="HS256",
    )


def decode(token: str) -> dict:
    return j.decode(
        jwt=token,
        key=settings.SECRET_KEY,
        algorithms=["HS256"],
    )
