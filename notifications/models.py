import uuid

from django.conf import settings
from django.contrib.auth.models import AbstractUser
from django.contrib.contenttypes.fields import GenericForeignKey
from django.contrib.contenttypes.models import ContentType
from django.db import models
from django.db.models import Case, Count, OuterRef, Subquery, When

from core.models import MessageConvertible, UUIDModel
from protos import notification_pb2


def delete_notifications_for(instance: models.Model):
    Notification.objects.filter(
        target_type=ContentType.objects.get_for_model(type(instance)),
        target_id=instance.id,
    ).delete()


class NotificationQuerySet(models.QuerySet):
    def filter_readable_by(self, user: AbstractUser, *args, **kwargs):
        return (
            self.filter(
                models.Q(recipient__isnull=True) | models.Q(recipient=user),
                *args,
                **kwargs,
            )
            if user.is_staff
            else self.filter(recipient=user, *args, **kwargs)
        )


class NotificationsManager(models.Manager):
    def get_queryset(self) -> NotificationQuerySet:
        return NotificationQuerySet(self.model).annotate(
            count=Count("count_units"),
            importance=Case(When(recipient__isnull=True, then=1), default=0),
        )

    def filter_readable_by(self, user: AbstractUser, *args, **kwargs):
        return self.get_queryset().filter_readable_by(user, *args, **kwargs)


class FlagsManager(NotificationsManager):
    def get_queryset(self) -> NotificationQuerySet:
        return super().get_queryset().filter(recipient__isnull=True)


class Notification(UUIDModel, MessageConvertible):
    class Meta:
        unique_together = ["recipient", "target_type", "target_id"]
        ordering = ["date_updated", "id"]

    objects = NotificationsManager()
    flag_objects = FlagsManager()
    default_message_class = notification_pb2.Notification

    recipient = models.ForeignKey(
        to=settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="%(class)ss",
        null=True,
    )
    target_type = models.ForeignKey(
        ContentType, on_delete=models.CASCADE, related_name="+"
    )
    target_id = models.CharField(max_length=len(str(uuid.uuid4())))
    target = GenericForeignKey("target_type", "target_id")
    date_updated = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"{self.recipient}: {self.target_type}/{self.target_id}"

    @staticmethod
    def get_count_query():
        return Subquery(CountUnit.objects.filter(notification_id=OuterRef("id")))

    def get_message_field_values(self, **overrides) -> dict:
        values = super().get_message_field_values(**overrides)
        values["is_flag"] = not self.recipient
        values[self.target_type.model.lower()] = self.target.to_message(
            context=self._context
        )
        return values


class CountUnit(UUIDModel):
    class Meta:
        unique_together = ["notification", "count_item_type", "count_item_id"]

    notification = models.ForeignKey(
        to=Notification, on_delete=models.CASCADE, related_name="count_units"
    )
    count_item_type = models.ForeignKey(
        ContentType, on_delete=models.CASCADE, related_name="+"
    )
    count_item_id = models.CharField(max_length=len(str(uuid.uuid4())))
    count_item = GenericForeignKey("count_item_type", "count_item_id")

    def __str__(self) -> str:
        return f"{self.notification}: ({self.count_item_type}/{self.count_item_id})"
