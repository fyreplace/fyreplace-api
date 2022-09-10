import uuid

from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractUser
from django.contrib.contenttypes.fields import GenericForeignKey
from django.contrib.contenttypes.models import ContentType
from django.db import models
from django.db.models import Case, When

from core.models import MessageConvertible, UUIDModel
from posts.models import Comment, Subscription
from protos import notification_pb2


def remove_notifications_for(instance: models.Model):
    target_type = ContentType.objects.get_for_model(type(instance))
    Notification.objects.filter(target_type=target_type, target_id=instance.id).delete()
    Flag.objects.filter(target_type=target_type, target_id=instance.id).delete()


class NotificationQuerySet(models.QuerySet):
    def filter_readable_by(self, user: AbstractUser, *args, **kwargs):
        return (
            self.filter(
                models.Q(subscription__isnull=True) | models.Q(subscription__user=user),
                *args,
                **kwargs,
            )
            if user.is_staff
            else self.filter(*args, subscription__user=user, **kwargs)
        )


class NotificationsManager(models.Manager):
    def get_queryset(self) -> NotificationQuerySet:
        return NotificationQuerySet(self.model).annotate(
            is_flag=models.ExpressionWrapper(
                models.Q(subscription__isnull=True), output_field=models.BooleanField()
            ),
            importance=Case(When(subscription__isnull=True, then=1), default=0),
        )

    def filter_readable_by(self, user: AbstractUser, *args, **kwargs):
        return self.get_queryset().filter_readable_by(user, *args, **kwargs)


class FlagsManager(NotificationsManager):
    def get_queryset(self) -> NotificationQuerySet:
        return super().get_queryset().filter(subscription__isnull=True)


class Notification(UUIDModel, MessageConvertible):
    class Meta:
        unique_together = ["subscription", "target_type", "target_id"]
        ordering = ["date_updated", "id"]

    objects = NotificationsManager()
    flag_objects = FlagsManager()
    default_message_class = notification_pb2.Notification

    subscription = models.OneToOneField(
        to=Subscription,
        on_delete=models.CASCADE,
        related_name="+",
        null=True,
    )
    target_type = models.ForeignKey(
        ContentType, on_delete=models.CASCADE, related_name="+"
    )
    target_id = models.CharField(max_length=len(str(uuid.uuid4())))
    target = GenericForeignKey("target_type", "target_id")
    count = models.IntegerField(default=0)
    date_updated = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"{self.target_type}/{self.target_id} ({self.count})"

    def save(self, *args, **kwargs):
        if subscription := self.subscription:
            if comment := subscription.last_comment_seen:
                self.count = comment.count(after=True)
            else:
                self.count = Comment.objects.filter(
                    post_id=subscription.post_id
                ).count()
        else:
            self.count = Flag.objects.filter(
                target_type=self.target_type, target_id=self.target_id
            ).count()

        super().save(*args, **kwargs)

    def get_message_field_values(self, **overrides) -> dict:
        values = super().get_message_field_values(**overrides)
        target_field = self.target_type.model.lower()
        values[target_field] = self.target.to_message(
            message_class=self.retrieve_message_class(target_field),
            context=self._context,
            is_preview=True,
        )
        return values


class Flag(UUIDModel):
    class Meta:
        unique_together = ["issuer", "target_type", "target_id"]

    issuer = models.ForeignKey(
        to=get_user_model(), on_delete=models.CASCADE, related_name="+"
    )
    target_type = models.ForeignKey(
        ContentType, on_delete=models.CASCADE, related_name="+"
    )
    target_id = models.CharField(max_length=len(str(uuid.uuid4())))
    target = GenericForeignKey("target_type", "target_id")

    def __str__(self) -> str:
        return f"{self.notification}: ({self.count_item_type}/{self.count_item_id})"
