from typing import Type
from uuid import UUID

from django.contrib.contenttypes.models import ContentType
from django.db.models import Model
from django.test.testcases import TestCase

from .models import Notification


class BaseNotificationTestCase(TestCase):
    def assertNoFlags(self, model: Type[Model], target_id: UUID):
        self.assertFalse(
            Notification.objects.filter(
                recipient__isnull=True,
                target_type=ContentType.objects.get_for_model(model),
                target_id=target_id,
            ).exists()
        )
