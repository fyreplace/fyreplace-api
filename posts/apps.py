from django.apps import AppConfig


class PostsConfig(AppConfig):
    name = "posts"
    verbose_name = "Posts"
    default_auto_field = "django.db.models.BigAutoField"

    def ready(self):
        super().ready()
        from . import signals
