import health_check.urls
from django.conf import settings
from django.conf.urls.static import static
from django.contrib import admin
from django.urls import include, path

import posts.urls
import users.urls

urlpatterns = [
    path("api/", include(users.urls)),
    path("api/", include(posts.urls)),
    path("admin/", admin.site.urls),
    path("health/", include(health_check.urls)),
    *static(settings.STATIC_URL, document_root=settings.STATIC_ROOT),
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
