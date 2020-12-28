from django.apps import AppConfig


class NotificationsConfig(AppConfig):
    name = "notifications"
    verbose_name = "Notifications"

    def ready(self):
        super().ready()
        from . import signals
