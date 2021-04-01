from django.apps import AppConfig


class UsersConfig(AppConfig):
    name = __package__
    verbose_name = "Users"
    default_auto_field = "django.db.models.BigAutoField"

    def ready(self):
        super().ready()
        from . import signals
