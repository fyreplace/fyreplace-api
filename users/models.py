from datetime import timedelta
from secrets import token_urlsafe
from typing import Dict, Tuple

from django.conf import settings
from django.contrib.auth.models import AbstractUser
from django.core.validators import MinLengthValidator
from django.db import models
from django.utils.timezone import now
from django.utils.translation import gettext as _

from core.models import SoftDeleteModel, UUIDModel
from core.validators import FileSizeValidator


class Block(models.Model):
    class Meta:
        constraints = [
            models.CheckConstraint(
                check=~models.Q(issuer=models.F("target")),
                name="issuer_ne_target",
            )
        ]
        unique_together = ["issuer", "target"]
        ordering = unique_together

    issuer = models.ForeignKey(
        to=settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="+",
    )
    target = models.ForeignKey(
        to=settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="+",
    )


class User(AbstractUser, UUIDModel, SoftDeleteModel):
    class Meta:
        ordering = ["username", "date_joined", "id"]

    username = models.CharField(
        max_length=50,
        validators=[AbstractUser.username_validator, MinLengthValidator(3)],
        unique=True,
        null=True,
        blank=True,
    )
    email = models.EmailField(unique=True, null=True, blank=True)
    avatar = models.ImageField(
        upload_to="avatars",
        validators=[FileSizeValidator(max_megabytes=1)],
        null=True,
        blank=True,
    )
    bio = models.TextField(max_length=3000, blank=True)
    blocked_users = models.ManyToManyField(
        to=settings.AUTH_USER_MODEL,
        related_name="+",
        through=Block,
    )
    is_banned = models.BooleanField(default=False)
    date_ban_end = models.DateTimeField(null=True)

    def __str__(self) -> str:
        if self.is_deleted:
            return _("Deleted user")
        elif self.is_banned and self.date_ban_end is None:
            return _("Banned user")
        else:
            return super().__str__()

    def delete(self, *args, **kwargs) -> Tuple[int, Dict[str, int]]:
        return self.soft_delete()

    def ban(self, duration: timedelta):
        from .signals import post_ban, pre_ban

        pre_ban.send(sender=self.__class__, instance=self)
        self.is_banned = True
        self.date_ban_end = now() + duration
        self.save()
        post_ban.send(sender=self.__class__, instance=self)

    @property
    def avatar_img(self) -> str:
        return mark_safe(f'<img src="{self.avatar}" width="128" height="128">')


class Token(models.Model):
    class Meta:
        ordering = ["-date_last_used", "user", "key"]

    key = models.CharField(max_length=64, primary_key=True, unique=True, editable=False)
    user = models.ForeignKey(
        to=settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="+",
        editable=False,
    )
    date_last_used = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return self.key

    def save(self, *args, **kwargs) -> None:
        if not self.key:
            self.key = token_urlsafe(32)

        super().save(*args, **kwargs)
