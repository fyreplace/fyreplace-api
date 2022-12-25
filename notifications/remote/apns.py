from datetime import timedelta
from http.client import GONE, NOT_FOUND
from math import floor
from typing import Iterable
from urllib.parse import urljoin

import jwt
from celery import shared_task
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


@shared_task
def send_remote_notifications_comment_change(comment_id: str):
    comment = Comment.objects.get(id=comment_id)
    users = comment.post.subscribers.exclude(id=comment.author_id)

    for cursor in range(0, users.count(), 500):
        chunk = users[cursor : cursor + 500]
        send_message(
            comment,
            chunk,
            "comment:" + ("deletion" if comment.is_deleted else "creation"),
        )


@shared_task
def send_remote_notifications_comment_acknowledgement(comment_id: str, user_id: str):
    comment = Comment.objects.get(id=comment_id)

    if user_id == comment.author_id:
        return

    send_message(
        comment, [get_user_model().objects.get(id=user_id)], "comment:acknowledgement"
    )


def send_message(comment: Comment, users: Iterable[AbstractUser], command: str):
    if not settings.APNS_PRIVATE_KEY:
        return

    payload = make_payload(comment, command)

    with Client(http2=True, headers=make_headers(comment, command)) as client:
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
    return (
        jwt.encode(
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
        if settings.APNS_PRIVATE_KEY
        else ""
    )


def make_headers(comment: Comment, command: str) -> dict:
    future = now() + timedelta(days=10)
    headers = {
        "authorization": f"bearer {ApnsToken.objects.last().token}",
        "apns-push-type": "alert" if command == "comment:creation" else "background",
        "apns-expiration": str(round(future.timestamp())),
        "apns-topic": settings.APPLE_APP_ID,
        "apns-id": str(comment.id),
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
    is_silent = payload["_command"] != "comment:creation"
    badge = count_notifications_for(user)
    relevance_score = float(user.id == comment.post.author_id)
    json = {
        "aps": (
            {"content-available": 1}
            if is_silent
            else {
                "badge": badge,
                "alert": {"title": comment.author.username, "body": comment.text},
                "thread-id": b64encode(comment.post_id),
                "relevance-score": relevance_score,
            }
        ),
        **payload,
    }

    if is_silent:
        json = {**json, "_aps.badge": badge, "_aps.relevance-score": relevance_score}

    return json
