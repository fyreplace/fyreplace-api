from django.contrib import admin
from django.contrib.admin.apps import AdminConfig


class CoreConfig(AdminConfig):
    default_site = "core.admin.AdminSite"

    def ready(self):
        super().ready()

        from django.contrib.auth.models import Group
        from django_celery_beat.models import (
            ClockedSchedule,
            CrontabSchedule,
            IntervalSchedule,
            PeriodicTask,
            SolarSchedule,
        )

        for model in [
            Group,
            ClockedSchedule,
            CrontabSchedule,
            IntervalSchedule,
            SolarSchedule,
            PeriodicTask,
        ]:
            admin.site.unregister(model)
