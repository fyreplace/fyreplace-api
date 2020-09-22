from django.apps import AppConfig


class PostsConfig(AppConfig):
    name = "posts"
    verbose_name = "Posts"

    def ready(self):
        super().ready()
        from . import signals
