import os

from celery import Celery
from celery.signals import task_failure
from django.conf import settings

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "core.settings")

app = Celery(settings.APP_NAME)
app.config_from_object("django.conf:settings", namespace="CELERY")
app.autodiscover_tasks()


@task_failure.connect
def on_task_failure(**kwargs):
    pass
