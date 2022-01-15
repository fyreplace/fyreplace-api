from datetime import timedelta
from typing import Dict, List, Optional, Tuple

from django.conf import settings
from django.contrib.auth.models import AbstractUser, UserManager
from django.core.validators import MaxLengthValidator, MinLengthValidator
from django.db import models
from django.utils.timezone import now
from django.utils.translation import gettext as _

from core import jwt
from core.models import SoftDeleteModel, TimestampModel, UUIDModel
from core.validators import FileSizeValidator
from protos import user_pb2


class Block(UUIDModel):
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


class ExistingUserManager(UserManager):
    def get_queryset(self) -> models.QuerySet:
        return super().get_queryset().filter(is_deleted=False)


class User(AbstractUser, UUIDModel, SoftDeleteModel):
    class Meta:
        ordering = ["username", "date_joined", "id"]

    existing_objects = ExistingUserManager()
    default_message_class = user_pb2.User

    username = models.CharField(
        max_length=50,
        validators=[AbstractUser.username_validator, MinLengthValidator(3)],
        unique=True,
        null=True,
    )
    email = models.EmailField(unique=True, null=True)
    avatar = models.ImageField(
        upload_to="avatars",
        validators=[FileSizeValidator(max_megabytes=1)],
        null=True,
        blank=True,
    )
    bio = models.TextField(
        max_length=3000, blank=True, validators=[MaxLengthValidator(3000)]
    )
    blocked_users = models.ManyToManyField(
        to=settings.AUTH_USER_MODEL,
        related_name="+",
        through=Block,
    )
    is_banned = models.BooleanField(default=False)
    date_ban_end = models.DateTimeField(null=True, blank=True)

    @property
    def is_alive(self) -> bool:
        return not (self.is_deleted or self.is_banned)

    @property
    def is_alive_and_kicking(self) -> bool:
        return self.is_alive and self.is_active

    @property
    def is_pending(self) -> bool:
        return self.is_alive and not self.is_active

    @property
    def profile(self) -> "User":
        return self

    @property
    def rank(self) -> user_pb2.Rank:
        if self.is_superuser:
            return user_pb2.RANK_SUPERUSER
        elif self.is_staff:
            return user_pb2.RANK_STAFF
        else:
            return user_pb2.RANK_CITIZEN

    def __str__(self) -> str:
        if self.is_deleted:
            return f"{_('Deleted user')} {self.id}"
        elif self.is_banned and self.date_ban_end is None:
            return f"{_('Banned user')} {self.id}"
        else:
            return super().__str__()

    def get_message_fields(self, **overrides) -> List[str]:
        if overrides.get("is_banned", self.is_banned) and not self.date_ban_end:
            if self._message_class == user_pb2.User:
                return ["profile", "date_joined"]
            elif self._message_class == user_pb2.Profile:
                return ["id", "is_banned"]
            else:
                return []

        return super().get_message_fields(**overrides)

    def delete(self, *args, **kwargs) -> Tuple[int, Dict[str, int]]:
        return self.soft_delete()

    def perform_soft_delete(self):
        self.username = None
        self.email = None
        self.set_unusable_password()
        self.avatar.delete(save=False)
        self.bio = ""
        super().perform_soft_delete()

    def ban(self, duration: Optional[timedelta] = None):
        from .signals import post_ban, pre_ban

        pre_ban.send(sender=self.__class__, instance=self)
        self.is_banned = True

        if duration:
            self.date_ban_end = now() + duration

        self.save()
        post_ban.send(sender=self.__class__, instance=self)


class Hardware(models.TextChoices):
    DESKTOP = "desktop", _("Desktop")
    MOBILE = "mobile", _("Mobile")
    WATCH = "watch", _("Watch")
    UNKNOWN = "unknown", _("Unknown")


class Software(models.TextChoices):
    ANDROID = "android", _("Android")
    BSD = "bsd", _("BSD")
    DARWIN = "darwin", _("Darwin")
    LINUX = "linux", _("Linux")
    WINDOWS = "windows", _("Windows")
    UNKNOWN = "unknown", _("Unknown")


class Connection(UUIDModel, TimestampModel):
    class Meta:
        constraints = [
            models.CheckConstraint(
                check=models.Q(hardware__in=Hardware.values),
                name="hardware",
            ),
            models.CheckConstraint(
                check=models.Q(software__in=Software.values),
                name="software",
            ),
        ]
        ordering = [
            "date_last_used",
            "date_created",
            "hardware",
            "software",
            "id",
        ]

    default_message_class = user_pb2.Connection

    user = models.ForeignKey(
        to=settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="%(class)ss"
    )
    date_last_used = models.DateTimeField(auto_now=True)
    hardware = models.CharField(
        max_length=30, choices=Hardware.choices, default=Hardware.UNKNOWN
    )
    software = models.CharField(
        max_length=30, choices=Software.choices, default=Software.UNKNOWN
    )

    def __str__(self) -> str:
        return f"{self.user}: {self.hardware}/{self.software} ({self.date_created})"

    def get_message_field_values(self, **overrides) -> dict:
        data = super().get_message_field_values(**overrides)
        data["client"] = user_pb2.Client(hardware=self.hardware, software=self.software)
        return data

    def get_token(self) -> str:
        payload = {"user_id": str(self.user_id), "connection_id": str(self.id)}
        return jwt.encode(payload)
