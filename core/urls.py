import health_check.urls
from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.urls import include, path
from rest_framework.schemas import get_schema_view

from .views import robots_txt

urlpatterns = [
    path("health/", include(health_check.urls)),
    path("robots.txt", robots_txt),
    path(
        "openapi/",
        get_schema_view(
            title=settings.APP_NAME,
            authentication_classes=[],
            permission_classes=[],
        ),
        name="openapi",
    ),
    path("admin/", admin.site.urls),
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
