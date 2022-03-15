from django.apps.config import AppConfig
from django.db.models import Field

from .lookups import BytesLookup


class CoreConfig(AppConfig):
    name = __package__
    verbose_name = "Core"
    default_auto_field = "django.db.models.BigAutoField"

    def ready(self):
        super().ready()
        Field.register_lookup(BytesLookup)
