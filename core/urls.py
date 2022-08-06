from django.conf import settings
from django.conf.urls.static import static
from django.urls import path

from .views import health

urlpatterns = [path("health/", health)]

if settings.DEBUG:
    urlpatterns += static(settings.STATIC_URL, document_root=settings.STATIC_ROOT)
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
