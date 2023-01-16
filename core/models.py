import uuid

from django.db import models

from .signals import post_soft_delete, pre_soft_delete


class MessageConvertible:
    pass


class UUIDModel(models.Model):
    class Meta:
        abstract = True

    id = models.UUIDField(primary_key=True, unique=True, default=uuid.uuid4)


class TimestampModel(models.Model):
    class Meta:
        abstract = True

    date_created = models.DateTimeField(auto_now_add=True)


class SoftDeleteModel(models.Model):
    class Meta:
        abstract = True

    is_deleted = models.BooleanField(default=False)

    def soft_delete(self) -> tuple[int, dict[str, int]]:
        pre_soft_delete.send(sender=self.__class__, instance=self)
        self.perform_soft_delete()
        post_soft_delete.send(sender=self.__class__, instance=self)
        return 0, {}

    def perform_soft_delete(self):
        self.is_deleted = True
        self.save()


class ExistingManager(models.Manager):
    def get_queryset(self) -> models.QuerySet:
        return super().get_queryset().filter(is_deleted=False)
