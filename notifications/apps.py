from django.apps import AppConfig


class NotificationsConfig(AppConfig):
    name = __package__
    verbose_name = "Notifications"
    default_auto_field = "django.db.models.BigAutoField"

    def ready(self):
        super().ready()
        from . import signals
