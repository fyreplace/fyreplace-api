import logging
import os
from typing import Any, Dict, List

from celery import Celery
from celery.signals import task_failure
from django.conf import settings

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "core.settings")

app = Celery(settings.APP_NAME)
app.config_from_object("django.conf:settings", namespace="CELERY")
app.autodiscover_tasks()


@task_failure.connect
def on_task_failure(sender: Any, args: List, kwargs: Dict, **__):
    logging.error(
        msg=f"Task failed: {sender}",
        exc_info=True,
        extra={
            "arguments": args,
            "keyword_arguments": kwargs,
        },
    )
