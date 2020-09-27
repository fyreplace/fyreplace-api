from enum import Enum

from django.conf import settings
from django.db import models

from posts.models import Comment, Post


class Notification(models.Model):
    class Meta:
        unique_together = ["user", "post"]
        ordering = ["date_latest_comment", *unique_together]

    user = models.ForeignKey(
        to=settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="%(class)ss",
        editable=False,
    )
    post = models.ForeignKey(
        to=Post,
        on_delete=models.CASCADE,
        related_name="+",
        editable=False,
    )
    comments = models.ManyToManyField(to=Comment, related_name="+")
    date_latest_comment = models.DateTimeField(auto_now=True)
    received = models.BooleanField(default=False)


class Importance(Enum):
    DEFAULT = "default"
    COMMENT_OWN_POST = "comment_own_post"
