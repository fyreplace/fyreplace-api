from datetime import timedelta
from http.client import GONE, NOT_FOUND
from math import floor
from typing import Iterable
from urllib.parse import urljoin

import jwt
from django.conf import settings
from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractUser
from django.utils.timezone import now
from google.protobuf.json_format import MessageToDict
from httpx import Client

from posts.models import Comment
from users.models import Block, Connection

from ..models import (
    ApnsToken,
    MessagingService,
    RemoteMessaging,
    count_notifications_for,
)
from . import b64encode


def send_remote_notifications_comment_change(comment: Comment):
    send_message(
        comment,
        comment.post.subscribers.exclude(id=comment.author_id).iterator(chunk_size=500),
        "comment:" + ("deletion" if comment.is_deleted else "creation"),
    )


def send_remote_notifications_comment_acknowledgement(comment: Comment, user_id: str):
    if user_id == comment.author_id:
        return

    send_message(
        comment, [get_user_model().objects.get(id=user_id)], "comment:acknowledgement"
    )


def send_message(comment: Comment, users: Iterable[AbstractUser], command: str):
    payload = make_payload(comment, command)

    with Client(http2=True, headers=make_headers()) as client:
        for user in users:
            if Block.objects.filter(issuer=user, target=comment.author).exists():
                continue

            for remote_messaging in RemoteMessaging.objects.filter(
                service=MessagingService.APNS,
                connection__in=Connection.objects.filter(user=user),
            ):
                response = client.post(
                    make_url(remote_messaging.token),
                    json=make_json(payload, comment, user),
                )

                if response.status_code in (NOT_FOUND, GONE):
                    remote_messaging.delete()
                else:
                    response.raise_for_status()


def make_jwt() -> str:
    return jwt.encode(
        headers={
            "alg": "ES256",
            "kid": settings.APNS_PRIVATE_KEY_ID,
        },
        payload={
            "iat": floor(now().timestamp()),
            "iss": settings.APPLE_TEAM_ID,
        },
        key=settings.APNS_PRIVATE_KEY,
        algorithm="ES256",
    )


def make_headers() -> dict:
    future = now() + timedelta(days=10)
    headers = {
        "authorization": f"bearer {ApnsToken.objects.last().token}",
        "apns-push-type": "background",
        "apns-expiration": str(round(future.timestamp())),
        "apns-priority": "5",
        "apns-topic": settings.APPLE_APP_ID,
    }

    return headers


def make_url(token: str) -> str:
    return urljoin(settings.APNS_URL, f"/3/device/{token}")


def make_payload(comment: Comment, command: str) -> dict:
    return {
        "_command": command,
        "comment": MessageToDict(comment.to_message()),
        "postId": b64encode(comment.post_id),
    }


def make_json(payload: dict, comment: Comment, user: AbstractUser) -> dict:
    return {
        "aps": {"content-available": 1},
        "_aps.badge": count_notifications_for(user),
        "_aps.relevance-score": float(user.id == comment.post.author_id),
        **payload,
    }
