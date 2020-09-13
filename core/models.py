import uuid
from typing import Dict, Tuple

from django.db import models

from .signals import post_soft_delete, pre_soft_delete


class UUIDModel(models.Model):
    class Meta:
        abstract = True

    id = models.UUIDField(
        primary_key=True,
        unique=True,
        default=uuid.uuid4,
        editable=False,
    )


class SoftDeleteModel(models.Model):
    class Meta:
        abstract = True

    is_deleted = models.BooleanField(default=False)

    def soft_delete(self) -> Tuple[int, Dict[str, int]]:
        pre_soft_delete.send(sender=self.__class__, instance=self)
        self.is_deleted = True
        self.save()
        post_soft_delete.send(sender=self.__class__, instance=self)
        return 0, {}
