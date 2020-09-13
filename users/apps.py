from django.apps import AppConfig


class UsersConfig(AppConfig):
    name = "users"
    verbose_name = "Users"

    def ready(self):
        super().ready()
        from . import signals
