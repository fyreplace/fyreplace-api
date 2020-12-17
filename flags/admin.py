from datetime import timedelta

from django.db.models import Model
from django.utils.timezone import now

from .models import Flag


def last_week_flags(obj: Model) -> int:
    deadline = now() - timedelta(weeks=1)
    return Flag.objects.filter(target_id=obj.id, date_created__gt=deadline).count()
