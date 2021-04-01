from datetime import timedelta
from math import ceil
from typing import Dict, Optional, Tuple

from django.conf import settings
from django.contrib.auth.base_user import AbstractBaseUser
from django.core.validators import MaxLengthValidator, MinValueValidator
from django.db import IntegrityError, models
from django.db.models.functions import Replace
from django.db.transaction import atomic
from django.utils.timezone import now
from grpc_interceptor.exceptions import InvalidArgument, PermissionDenied

from core.models import (
    ExistingManager,
    MessageConvertible,
    SoftDeleteModel,
    TimestampModel,
    UUIDModel,
)
from core.validators import FileSizeValidator
from protos import comment_pb2, post_pb2


def position_between(before: Optional[str], after: Optional[str]) -> str:
    for arg in (before, after):
        if not isinstance(arg, (str, type(None))):
            raise TypeError("Arguments must be strings or None")

    len_before = len(before) if before else 0
    len_after = len(after) if after else 0

    if not before and not after:
        return "z"
    elif before == after:
        raise RuntimeError("Arguments must be different")
    elif before and after and before > after:
        raise RuntimeError("Before and after are inverted")
    elif not after or len_before >= len_after:
        return before + "z"
    else:
        return after[:-1] + "a" + "z"


class ValidatableModel(models.Model, MessageConvertible):
    class Meta:
        abstract = True

    def validate(self):
        raise NotImplementedError


class PostQuerySet(models.QuerySet):
    def get_readable_by(self, author: AbstractBaseUser, *args, **kwargs) -> "Post":
        return self.get(
            models.Q(author=author) | models.Q(date_published__isnull=False),
            *args,
            **kwargs,
        )

    def get_writable_by(self, author: AbstractBaseUser, *args, **kwargs) -> "Post":
        post = self.get_readable_by(author, *args, **kwargs)

        if author.id != post.author_id or post.date_published:
            raise PermissionDenied(
                "post_published" if post.date_published else "invalid_post_author"
            )

        return post

    def get_published_readable_by(
        self, author: AbstractBaseUser, *args, **kwargs
    ) -> "Post":
        post = self.get_readable_by(author, *args, **kwargs)

        if not post.date_published:
            raise PermissionDenied("post_not_published")

        return post


class ExistingPostManager(models.Manager):
    def get_queryset(self) -> PostQuerySet:
        return PostQuerySet(self.model).filter(is_deleted=False)

    def get_readable_by(self, *args, **kwargs):
        return self.get_queryset().get_readable_by(*args, **kwargs)

    def get_writable_by(self, *args, **kwargs):
        return self.get_queryset().get_writable_by(*args, **kwargs)

    def get_published_readable_by(self, *args, **kwargs):
        return self.get_queryset().get_published_readable_by(*args, **kwargs)


class PublishedPostManager(ExistingPostManager):
    def get_queryset(self) -> models.QuerySet:
        return super().get_queryset().filter(date_published__isnull=False)


class DraftPostManager(ExistingPostManager):
    def get_queryset(self) -> models.QuerySet:
        return (
            super()
            .get_queryset()
            .filter(date_published__isnull=True)
            .order_by("date_created", "id")
        )


class ActivePostManager(ExistingPostManager):
    def get_queryset(self) -> models.QuerySet:
        deadline = now() - timedelta(weeks=4)
        return super().get_queryset().filter(date_published__gte=deadline, life__gt=0)


class Post(UUIDModel, TimestampModel, SoftDeleteModel, ValidatableModel):
    class Meta:
        ordering = ["date_published", "id"]

    MAX_CHAPTERS = 10
    objects = models.Manager()
    existing_objects = ExistingPostManager()
    published_objects = PublishedPostManager()
    draft_objects = DraftPostManager()
    active_objects = ActivePostManager()
    default_message_class = post_pb2.Post

    author = models.ForeignKey(
        to=settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="%(class)ss"
    )
    voters = models.ManyToManyField(
        to=settings.AUTH_USER_MODEL,
        related_name="voted_%(class)ss",
        through="Vote",
    )
    subscribers = models.ManyToManyField(
        to=settings.AUTH_USER_MODEL,
        related_name="subscribed_%(class)ss",
        through="Subscription",
    )
    is_anonymous = models.BooleanField(default=False)
    date_published = models.DateTimeField(null=True)
    life = models.IntegerField(default=0, validators=[MinValueValidator(0)])

    def __str__(self) -> str:
        return f"{self.author}: {self.chapters.count()} ({self.date_published or self.date_created})"

    def get_message_field_values(self, **overrides) -> dict:
        data = super().get_message_field_values(**overrides)
        chapters = data["chapters"].all()

        if overrides.get("is_preview", False):
            chapters = chapters[:1]

        data["chapters"] = [self.convert_value("chapters", c) for c in chapters]
        return data

    def delete(self, *args, **kwargs) -> Tuple[int, Dict[str, int]]:
        return (
            super().delete(*args, **kwargs)
            if self.date_published is None
            else self.soft_delete()
        )

    def perform_soft_delete(self):
        self.life = 0
        super().perform_soft_delete()

    def publish(self, anonymous: bool):
        if self.date_published is not None:
            raise IntegrityError(self.error_already_published)

        self.validate()
        self.is_anonymous = anonymous
        self.date_published = now()
        self.life = 10
        self.save()
        self.subscribers.add(self.author)

    def validate(self):
        if self.chapters.count() == 0:
            raise InvalidArgument("post_empty")

        for chapter in self.chapters.all():
            chapter.validate()

    def chapter_position(self, position: int) -> str:
        positions = self.chapters.values_list("position", flat=True)
        chapter_count = len(positions)

        if position < 0 or position > chapter_count:
            raise InvalidArgument("invalid_position")

        before = None if position == 0 else positions[position - 1]
        after = (
            None
            if chapter_count == 0 or position >= chapter_count
            else positions[position]
        )
        return position_between(before, after)

    def get_chapter_at(self, position: int) -> "Chapter":
        try:
            return self.chapters.all()[position]
        except IndexError:
            raise InvalidArgument("invalid_position")

    @atomic
    def normalize_chapters(self):
        self.chapters.update(
            position=Replace(
                Replace(models.F("position"), models.Value("z"), models.Value("y")),
                models.Value("a"),
                models.Value("b"),
            )
        )
        chapters = self.chapters.all()
        a_length = ceil(len(chapters) / 2)

        for i, chapter in enumerate(chapters):
            if i < a_length:
                chapter.position = "a" * (a_length - i) + "z"
            else:
                chapter.position = "z" * (i - a_length + 1)

            chapter.save()


class Chapter(ValidatableModel):
    class Meta:
        unique_together = ["post", "position"]
        ordering = unique_together

    post = models.ForeignKey(
        to=Post, on_delete=models.CASCADE, related_name="%(class)ss"
    )
    position = models.CharField(max_length=128)
    text = models.TextField(
        max_length=500, blank=True, validators=[MaxLengthValidator(500)]
    )
    is_title = models.BooleanField(default=False)
    image = models.ImageField(
        width_field="width",
        height_field="height",
        upload_to="chapters",
        validators=[FileSizeValidator(max_megabytes=0.5)],
        null=True,
        blank=True,
    )
    width = models.IntegerField(
        null=True, blank=True, validators=[MinValueValidator(0)]
    )
    height = models.IntegerField(
        null=True, blank=True, validators=[MinValueValidator(0)]
    )

    def __str__(self) -> str:
        return f"{self.post}: {self.position}"

    def get_message_field_values(self, **options) -> dict:
        data = super().get_message_field_values(**options)

        if data["image"]:
            data["image"].width = self.width
            data["image"].height = self.height

        return data

    def validate(self):
        if not self.text and not self.image:
            raise InvalidArgument("chapter_empty")

    def clear(self, save: bool = True):
        self.text = ""
        self.is_title = False
        self.image.delete(save=save)


class Stack(models.Model):
    class Meta:
        ordering = ["user"]

    MAX_SIZE = 10

    user = models.OneToOneField(
        to=settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="%(class)s"
    )
    posts = models.ManyToManyField(to=Post, related_name="+", through="Visibility")
    date_last_filled = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return str(self.user)

    @atomic
    def fill(self):
        posts_count = self.posts.count()

        if posts_count >= self.MAX_SIZE:
            return

        post_ids = (
            Post.active_objects.exclude(
                models.Q(author=self.user)
                | models.Q(author__in=self.user.blocked_users.values("id"))
                | models.Q(voters=self.user)
            )
            .order_by("date_published")
            .values_list("id", flat=True)[: self.MAX_SIZE]
        )
        current_post_ids = list(self.posts.values_list("id", flat=True))
        self.posts.set(post_ids)
        self.posts.exclude(id__in=current_post_ids).update(life=models.F("life") - 1)
        self.save()

    @atomic
    def drain(self):
        self.posts.update(life=models.F("life") + 1)
        self.posts.clear()


class Vote(models.Model):
    class Meta:
        unique_together = ["user", "post"]
        ordering = unique_together

    user = models.ForeignKey(
        to=settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="+"
    )
    post = models.ForeignKey(to=Post, on_delete=models.CASCADE, related_name="+")
    spread = models.BooleanField()

    def __str__(self) -> str:
        return f"{self.user}, {self.post}: {self.spread}"


class Visibility(models.Model):
    class Meta:
        unique_together = ["stack", "post"]
        ordering = unique_together

    stack = models.ForeignKey(to=Stack, on_delete=models.CASCADE, related_name="+")
    post = models.ForeignKey(to=Post, on_delete=models.CASCADE, related_name="+")

    def __str__(self) -> str:
        return f"{self.stack}, {self.post}"


class Comment(UUIDModel, TimestampModel, SoftDeleteModel):
    class Meta:
        ordering = ["date_created", "id"]

    objects = models.Manager()
    existing_objects = ExistingManager()
    default_message_class = comment_pb2.Comment

    post = models.ForeignKey(
        to=Post, on_delete=models.CASCADE, related_name="%(class)ss"
    )
    author = models.ForeignKey(
        to=settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="+"
    )
    text = models.TextField(max_length=500, validators=[MaxLengthValidator(500)])

    def __str__(self) -> str:
        return f"{self.author}, {self.post} ({self.date_created})"

    def get_message_field_values(self, **overrides) -> dict:
        values = super().get_message_field_values(**overrides)

        if self.is_deleted:
            del values["text"]

        return values

    def delete(self, *args, **kwargs) -> Tuple[int, Dict[str, int]]:
        return self.soft_delete()

    def perform_soft_delete(self):
        self.text = ""
        super().perform_soft_delete()


class Subscription(models.Model):
    class Meta:
        unique_together = ["user", "post"]
        ordering = unique_together

    user = models.ForeignKey(
        to=settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="%(class)ss"
    )
    post = models.ForeignKey(
        to=Post, on_delete=models.CASCADE, related_name="%(class)ss"
    )
    last_comment_seen = models.ForeignKey(
        to=Comment, on_delete=models.SET_NULL, related_name="+", null=True
    )

    def __str__(self) -> str:
        return f"{self.user}, {self.post}"
