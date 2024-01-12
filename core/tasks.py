from datetime import timedelta

from celery import shared_task
from django.utils.timezone import now

from .models import CachedRequest


@shared_task
def cleanup_cached_requests():
    deadline = now() - timedelta(minutes=3)
    CachedRequest.objects.filter(date_created__lte=deadline).delete()
