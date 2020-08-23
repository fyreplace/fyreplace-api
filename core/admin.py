from typing import Optional

from django.conf import settings
from django.contrib import admin
from django.db.models import Model
from django.http import HttpRequest


class AdminSite(admin.AdminSite):
    site_title = settings.PRETTY_APP_NAME
    site_header = f"{settings.PRETTY_APP_NAME} administration"
    index_title = "Administration"


class ReadOnlyModelAdmin(admin.ModelAdmin):
    def has_add_permission(self, request: HttpRequest) -> bool:
        return False

    def has_change_permission(
        self, request: HttpRequest, obj: Optional[Model] = None
    ) -> bool:
        return False
