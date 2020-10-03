import uuid

from django.conf import settings
from django.contrib.contenttypes.fields import GenericForeignKey
from django.contrib.contenttypes.models import ContentType
from django.db import models
from django.utils.translation import gettext as _

from core.models import TimestampModel


class Flag(TimestampModel):
    class Meta:
        unique_together = ["issuer", "target_id"]
        ordering = unique_together

    issuer = models.ForeignKey(
        to=settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="+",
    )
    target_type = models.ForeignKey(ContentType, on_delete=models.CASCADE)
    target_id = models.CharField(max_length=len(str(uuid.uuid4())))
    target = GenericForeignKey("target_type", "target_id")
    comment = models.TextField(max_length=500, blank=True)
