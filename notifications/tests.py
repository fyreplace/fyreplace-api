from uuid import UUID

from django.contrib.contenttypes.models import ContentType
from django.db.models import Model
from django.test.testcases import TestCase

from .models import Flag, Notification


class BaseNotificationTestCase(TestCase):
    def assertNoFlags(self, model: type[Model], target_id: UUID):
        target_type = ContentType.objects.get_for_model(model)

        self.assertFalse(
            Notification.objects.filter(
                subscription__isnull=True, target_type=target_type, target_id=target_id
            ).exists()
        )

        self.assertFalse(
            Flag.objects.filter(target_type=target_type, target_id=target_id).exists()
        )
