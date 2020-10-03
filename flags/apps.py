from django.apps import AppConfig


class FlagsConfig(AppConfig):
    name = "flags"
    verbose_name = "Flags"

    def ready(self):
        super().ready()
        from . import signals
