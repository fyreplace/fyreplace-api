from typing import Optional, Union
from urllib.parse import urljoin

from django.conf import settings
from django.contrib import admin
from django.http.request import HttpRequest
from django.utils.safestring import mark_safe
from django.utils.translation import gettext_lazy as _
from rest_framework.reverse import reverse

from core.admin import ReadOnlyModelAdmin
from flags.admin import last_week_flags

from .models import Post


def author(obj: Post) -> str:
    if obj.is_anonymous:
        html = f"<i>{_('Anonymous')}</i>"
    else:
        url = reverse("admin:users_user_change", args=[obj.author_id])
        html = f'<a href="{url}">{obj.author}</a>'

    return mark_safe(html)


def chunks(obj: Post) -> str:
    html_chunks = []

    for chunk in obj.chunks.all():
        if chunk.text is not None:
            tag = "h3" if chunk.is_title else "p"
            html_chunks.append(f"<{tag}>{chunk.text}</{tag}>")
        elif chunk.image:
            image_url = urljoin(settings.MEDIA_URL, str(chunk.image))
            html_chunks.append(f'<img src="{image_url}" width="256">')

    return mark_safe("<br>".join(html_chunks))


@admin.register(Post)
class PostAdmin(ReadOnlyModelAdmin):
    date_hierarchy = "date_created"
    list_display = ("id", author, "date_published", "is_deleted")
    list_filter = ("is_deleted", "date_published")
    fieldsets = (
        (
            _("Metadata"),
            {"fields": (author, "date_published", "is_deleted")},
        ),
        (
            _("Content"),
            {"fields": (chunks,)},
        ),
        (
            _("Moderation"),
            {"fields": (last_week_flags,)},
        ),
    )

    def get_fieldsets(
        self, request: HttpRequest, obj: Optional[Post] = None
    ) -> Union[list, tuple]:
        fieldsets = super().get_fieldsets(request, obj)
        return fieldsets[0:1] if obj.is_deleted else fieldsets

    def has_delete_permission(
        self, request: HttpRequest, obj: Optional[Post] = None
    ) -> bool:
        return obj is None or not obj.is_deleted
