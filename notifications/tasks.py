from typing import List

from celery import shared_task

from posts.models import Comment

from .models import Notification


@shared_task
def notify_post_subscribers(comment_id: str):
    comment = Comment.objects.get(id=comment_id)

    for user in comment.post.subscribers.exclude(id=comment.author_id):
        notification, _ = Notification.objects.get_or_create(
            user=user, post=comment.post
        )
        notification.comments.add(comment)


@shared_task
def remove_comments_from_notifications(user_id: str, comment_ids: List[str]):
    comments = Comment.objects.filter(id__in=comment_ids)
    post_ids = comments.values("post_id")

    for notification in Notification.objects.filter(
        user_id=user_id, post_id__in=post_ids
    ):
        notification.comments.remove(*comment_ids)


@shared_task
def mark_notification_as_received(notification_ids: List[str]):
    Notification.objects.filter(id__in=notification_ids).update(received=True)
