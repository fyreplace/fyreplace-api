from django.apps.config import AppConfig


class CoreConfig(AppConfig):
    name = __package__
    verbose_name = "Core"
    default_auto_field = "django.db.models.BigAutoField"
