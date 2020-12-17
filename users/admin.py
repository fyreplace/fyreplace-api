from datetime import timedelta
from typing import Optional, Union
from urllib.parse import urljoin

from admin_object_actions.admin import ModelAdminObjectActionsMixin
from admin_object_actions.forms import AdminObjectActionForm
from django import forms
from django.conf import settings
from django.contrib import admin
from django.contrib.auth import get_user_model
from django.contrib.auth.admin import UserAdmin
from django.contrib.auth.models import AbstractUser
from django.forms.models import ModelForm
from django.http import HttpRequest
from django.utils.safestring import mark_safe
from django.utils.translation import gettext_lazy as _

from core.admin import ReadOnlyModelAdmin
from flags.admin import last_week_flags


def avatar(obj: AbstractUser) -> Optional[str]:
    if not obj.avatar:
        return "No avatar"

    avatar_url = urljoin(settings.MEDIA_URL, str(obj.avatar))
    return mark_safe(f'<img src="{avatar_url}" width="128">')


class UserBanForm(AdminObjectActionForm):
    class Meta:
        model = get_user_model()
        fields = ()

    CHOICES = [
        ("week", _("One week")),
        ("month", _("One month")),
        ("forever", _("Forever")),
    ]

    duration = forms.ChoiceField(choices=CHOICES)

    def do_object_action(self):
        duration = self.cleaned_data["duration"]

        if duration == "week":
            delta = timedelta(weeks=1)
        elif duration == "month":
            delta = timedelta(days=30)
        else:
            delta = None

        self.instance.ban(delta)


@admin.register(get_user_model())
class UserAdmin(ModelAdminObjectActionsMixin, UserAdmin, ReadOnlyModelAdmin):
    list_display = ("username", "is_active", "is_deleted", "is_banned")
    fieldsets = (
        (
            _("Personal info"),
            {"fields": ("username", "date_joined", avatar, "bio")},
        ),
        (
            _("Permissions"),
            {
                "fields": (
                    "is_staff",
                    "is_superuser",
                    "is_active",
                    "is_deleted",
                    "is_banned",
                )
            },
        ),
        (
            _("Moderation"),
            {"fields": (last_week_flags, "display_object_actions_detail")},
        ),
    )
    readonly_fields = ("display_object_actions_detail",)
    object_actions = [
        {
            "slug": "ban_user",
            "verbose_name": _("ban"),
            "verbose_name_past": _("banned"),
            "form_class": UserBanForm,
            "permission": "moderation",
        },
        {
            "slug": "remove_avatar",
            "verbose_name": _("remove avatar"),
            "verbose_name_title": _("remove avatar from"),
            "verbose_name_past": _("moderated"),
            "function": "remove_avatar",
            "permission": "moderation",
        },
        {
            "slug": "remove_bio",
            "verbose_name": _("remove bio"),
            "verbose_name_title": _("remove bio from"),
            "verbose_name_past": _("moderated"),
            "function": "remove_bio",
            "permission": "moderation",
        },
    ]

    def get_fieldsets(
        self, request: HttpRequest, obj: Optional[AbstractUser] = None
    ) -> Union[list, tuple]:
        fieldsets = super().get_fieldsets(request, obj)
        return fieldsets[0:2] if obj.is_deleted else fieldsets

    def has_delete_permission(
        self, request: HttpRequest, obj: Optional[AbstractUser] = None
    ) -> bool:
        return False

    def has_moderation_permission(
        self, request: HttpRequest, obj: Optional[AbstractUser] = None
    ):
        return True

    def remove_avatar(self, obj: AbstractUser, form: ModelForm):
        obj.avatar.delete()

    def remove_bio(self, obj: AbstractUser, form: ModelForm):
        obj.bio = ""
        obj.save()
