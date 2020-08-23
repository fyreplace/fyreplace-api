from rest_framework.routers import DefaultRouter


class DefaultRouter(DefaultRouter):
    def __init__(self, *args, **kwargs) -> None:
        super().__init__(*args, **kwargs)

        for route in self.routes:
            if route.name == "{basename}-list":
                route.mapping["delete"] = "clear"
