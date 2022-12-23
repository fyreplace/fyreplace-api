from typing import List, Optional

from django.conf import settings
from django.db.models import OuterRef
from firebase_admin import messaging

from posts.models import Comment
from users.models import Block, Connection

from ..models import MessagingService, RemoteMessaging
from . import b64encode


def send_remote_notifications_comment_change(comment: Comment):
    payload = make_payload(
        comment, "comment:" + ("deletion" if comment.is_deleted else "creation")
    )
    connections = Connection.objects.filter(
        user_id__in=comment.post.subscribers.exclude(id=comment.author_id).values("id")
    ).exclude(
        user_id__in=Block.objects.filter(
            issuer_id=OuterRef("user_id"), target_id=comment.author_id
        ).values("issuer_id")
    )
    own_remote_messagings = RemoteMessaging.objects.filter(
        service=MessagingService.FCM,
        connection_id__in=connections.filter(user_id=comment.post.author_id),
    )
    other_remote_messagings = RemoteMessaging.objects.filter(
        service=MessagingService.FCM,
        connection_id__in=connections.exclude(user_id=comment.post.author_id),
    )

    for remote_messagings, channel_id in zip(
        (own_remote_messagings, other_remote_messagings),
        ("comments_own_posts", "comments_other_posts"),
    ):
        for cursor in range(0, remote_messagings.count(), 500):
            chunk = remote_messagings[cursor : cursor + 500]
            batch_response = send_multicast_message(
                make_multicast_message(
                    list(chunk.values_list("token", flat=True)),
                    channel_id,
                    payload,
                    comment,
                )
            )

            if not batch_response:
                break

            for response, remote_messaging in zip(batch_response.responses, chunk):
                if not response.success:
                    remote_messaging.delete()


def send_remote_notifications_comment_acknowledgement(comment: Comment, user_id: str):
    if user_id == comment.author_id:
        return

    payload = make_payload(comment, "comment:acknowledgement")
    remote_messagings = RemoteMessaging.objects.filter(
        service=MessagingService.FCM,
        connection__in=Connection.objects.filter(user_id=user_id),
    )

    send_multicast_message(
        make_multicast_message(
            list(remote_messagings.values_list("token", flat=True)),
            "",
            payload,
            comment,
        )
    )


def send_multicast_message(
    message: messaging.MulticastMessage,
) -> Optional[messaging.BatchResponse]:
    return messaging.send_multicast(message) if settings.FIREBASE_APP else None


def make_payload(comment: Comment, command: str) -> dict:
    return {
        "_command": command,
        "comment": b64encode(
            comment.to_message().SerializeToString(deterministic=True)
        ),
        "postId": b64encode(comment.post_id),
    }


def make_multicast_message(
    tokens: List[str], channel_id: str, payload: dict, comment: Comment
) -> messaging.MulticastMessage:
    is_silent = payload["_command"] != "comment:creation"
    return messaging.MulticastMessage(
        tokens=tokens,
        android=messaging.AndroidConfig(
            notification=None
            if is_silent
            else messaging.AndroidNotification(
                title=comment.author.username,
                body=comment.text,
                tag=make_notification_tag(comment),
                channel_id=channel_id,
                event_timestamp=comment.date_created,
            ),
            data={"_fcm.channel": channel_id, **payload}
            if is_silent
            else payload,
        ),
    )


def make_notification_tag(comment: Comment) -> str:
    return f"{b64encode(comment.post_id)}:{b64encode(comment.id)}"
