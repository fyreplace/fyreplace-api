from datetime import timedelta
from typing import Dict, Tuple

from django.conf import settings
from django.core.validators import MaxValueValidator, MinValueValidator
from django.db import IntegrityError, models
from django.utils.timezone import now
from rest_framework.exceptions import ValidationError

from core.models import SoftDeleteModel, TimestampModel, UUIDModel
from core.validators import FileSizeValidator


class ValidatableModel(models.Model):
    class Meta:
        abstract = True

    def validate(self):
        raise NotImplementedError


class AlivePostManager(models.Manager):
    def get_queryset(self) -> models.QuerySet:
        deadline = now() - timedelta(weeks=4)
        return super().get_queryset().filter(date_published__gte=deadline, life__gt=0)


class Post(UUIDModel, TimestampModel, SoftDeleteModel, ValidatableModel):
    class Meta:
        ordering = ["-date_published", "-date_created", "author", "id"]

    MAX_CHUNKS = 10
    objects = models.Manager()
    alive_objects = AlivePostManager()

    author = models.ForeignKey(
        to=settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="%(class)ss",
        editable=False,
    )
    voters = models.ManyToManyField(
        to=settings.AUTH_USER_MODEL,
        related_name="voted_%(class)ss",
        through="Vote",
    )
    subscribers = models.ManyToManyField(
        to=settings.AUTH_USER_MODEL,
        related_name="subscribed_%(class)ss",
    )
    is_anonymous = models.BooleanField(default=False)
    date_published = models.DateTimeField(null=True)
    life = models.IntegerField(default=0, validators=[MinValueValidator(0)])

    def delete(self, *args, **kwargs) -> Tuple[int, Dict[str, int]]:
        return (
            super().delete(*args, **kwargs)
            if self.date_published is None
            else self.soft_delete()
        )

    def publish(self, anonymous: bool):
        if self.date_published is not None:
            raise IntegrityError("Post is already published.")

        self.validate()
        self.is_anonymous = anonymous
        self.date_published = now()
        self.life = 10
        self.save()
        self.subscribers.add(self.author)

    def validate(self):
        if self.chunks.count() == 0:
            raise ValidationError("Post is empty.")

        for chunk in self.chunks.all():
            chunk.validate()


class Chunk(ValidatableModel):
    class Meta:
        unique_together = ["post", "position"]
        ordering = unique_together

    post = models.ForeignKey(
        to=Post,
        on_delete=models.CASCADE,
        related_name="%(class)ss",
        editable=False,
    )
    position = models.IntegerField(
        blank=True,
        validators=[MinValueValidator(0), MaxValueValidator(Post.MAX_CHUNKS - 1)],
    )
    text = models.TextField(max_length=500, null=True, blank=True)
    is_title = models.BooleanField(default=False)
    image = models.ImageField(
        width_field="width",
        height_field="height",
        upload_to="chunks",
        validators=[FileSizeValidator(max_megabytes=0.5)],
        null=True,
    )
    width = models.IntegerField(null=True, validators=[MinValueValidator(0)])
    height = models.IntegerField(null=True, validators=[MinValueValidator(0)])

    def validate(self):
        if self.text is not None:
            if len(self.text) == 0:
                raise ValidationError("Post text is empty.")
            elif self.image:
                raise ValidationError("Post chunk contains multiple data types.")
        elif not self.image:
            raise ValidationError("Post chunk is empty.")


class Stack(models.Model):
    class Meta:
        ordering = ["user"]

    user = models.OneToOneField(
        to=settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="%(class)s",
        editable=False,
    )
    posts = models.ManyToManyField(to=Post, related_name="+")
    date_last_filled = models.DateTimeField(auto_now=True)

    def fill(self):
        posts_count = self.posts.count()

        if posts_count >= 10:
            return

        posts = Post.alive_objects.exclude(
            models.Q(author=self.user)
            | models.Q(author__in=self.user.blocked_users.all())
            | models.Q(voters=self.user)
        )[:10]
        current_posts_ids = list(self.posts.values_list("id", flat=True))
        self.posts.set(posts)
        self.posts.exclude(id__in=current_posts_ids).update(life=models.F("life") - 1)
        self.save()

    def drain(self):
        self.posts.update(life=models.F("life") + 1)
        self.posts.clear()


class Vote(models.Model):
    class Meta:
        unique_together = ["user", "post"]
        ordering = unique_together

    user = models.ForeignKey(
        to=settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="+",
        editable=False,
    )
    post = models.ForeignKey(
        to=Post,
        on_delete=models.CASCADE,
        related_name="+",
        editable=False,
    )
    spread = models.BooleanField()


class Comment(UUIDModel, TimestampModel, SoftDeleteModel):
    class Meta:
        ordering = ["-date_created", "author", "id"]

    post = models.ForeignKey(
        to=Post,
        on_delete=models.CASCADE,
        related_name="%(class)ss",
        editable=False,
    )
    author = models.ForeignKey(
        to=settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="+",
        editable=False,
    )
    text = models.TextField(max_length=500)

    def delete(self, *args, **kwargs) -> Tuple[int, Dict[str, int]]:
        return self.soft_delete()
