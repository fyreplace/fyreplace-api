import uuid

import django.core.validators
import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models

import core.models
import core.validators


class Migration(migrations.Migration):

    initial = True

    dependencies = [
        ("posts", "0001_initial"),
    ]

    operations = [
        migrations.DeleteModel("Comment"),
        migrations.DeleteModel("Post"),
        migrations.DeleteModel("Stack"),
        migrations.DeleteModel("Vote"),
        migrations.DeleteModel("Visibility"),
        migrations.DeleteModel("Subscription"),
        migrations.DeleteModel("Chapter"),
        migrations.CreateModel(
            name="Comment",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        primary_key=True,
                        serialize=False,
                        unique=True,
                    ),
                ),
                ("date_created", models.DateTimeField(auto_now_add=True)),
                ("is_deleted", models.BooleanField(default=False)),
                (
                    "text",
                    models.TextField(
                        max_length=500,
                        validators=[django.core.validators.MaxLengthValidator(500)],
                    ),
                ),
                (
                    "author",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="+",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={
                "ordering": ["date_created", "id"],
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
        migrations.CreateModel(
            name="Post",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        primary_key=True,
                        serialize=False,
                        unique=True,
                    ),
                ),
                ("date_created", models.DateTimeField(auto_now_add=True)),
                ("is_deleted", models.BooleanField(default=False)),
                ("is_anonymous", models.BooleanField(default=False)),
                ("date_published", models.DateTimeField(null=True)),
                (
                    "life",
                    models.IntegerField(
                        default=0,
                        validators=[django.core.validators.MinValueValidator(0)],
                    ),
                ),
                (
                    "author",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="posts",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={
                "ordering": ["date_published", "date_created", "id"],
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
        migrations.CreateModel(
            name="Stack",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        primary_key=True,
                        serialize=False,
                        unique=True,
                    ),
                ),
                ("date_last_filled", models.DateTimeField(auto_now=True)),
            ],
            options={
                "ordering": ["user"],
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
        migrations.CreateModel(
            name="Vote",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        primary_key=True,
                        serialize=False,
                        unique=True,
                    ),
                ),
                ("spread", models.BooleanField()),
                (
                    "post",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="+",
                        to="posts.post",
                    ),
                ),
                (
                    "user",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="+",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={
                "ordering": ["user", "post"],
                "unique_together": {("user", "post")},
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
        migrations.CreateModel(
            name="Visibility",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        primary_key=True,
                        serialize=False,
                        unique=True,
                    ),
                ),
                (
                    "post",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="+",
                        to="posts.post",
                    ),
                ),
                (
                    "stack",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="+",
                        to="posts.stack",
                    ),
                ),
            ],
            options={
                "ordering": ["stack", "post"],
                "unique_together": {("stack", "post")},
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
        migrations.CreateModel(
            name="Subscription",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        primary_key=True,
                        serialize=False,
                        unique=True,
                    ),
                ),
                (
                    "last_comment_seen",
                    models.ForeignKey(
                        null=True,
                        on_delete=django.db.models.deletion.SET_NULL,
                        related_name="+",
                        to="posts.comment",
                    ),
                ),
                (
                    "post",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="subscriptions",
                        to="posts.post",
                    ),
                ),
                (
                    "user",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="subscriptions",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={
                "ordering": ["user", "post"],
                "unique_together": {("user", "post")},
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
        migrations.AddField(
            model_name="stack",
            name="posts",
            field=models.ManyToManyField(
                related_name="_posts_stack_posts_+",
                through="posts.Visibility",
                to="posts.Post",
            ),
        ),
        migrations.AddField(
            model_name="stack",
            name="user",
            field=models.OneToOneField(
                on_delete=django.db.models.deletion.CASCADE,
                related_name="stack",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AddField(
            model_name="post",
            name="subscribers",
            field=models.ManyToManyField(
                related_name="subscribed_posts",
                through="posts.Subscription",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AddField(
            model_name="post",
            name="voters",
            field=models.ManyToManyField(
                related_name="voted_posts",
                through="posts.Vote",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AddField(
            model_name="comment",
            name="post",
            field=models.ForeignKey(
                on_delete=django.db.models.deletion.CASCADE,
                related_name="comments",
                to="posts.post",
            ),
        ),
        migrations.CreateModel(
            name="Chapter",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        primary_key=True,
                        serialize=False,
                        unique=True,
                    ),
                ),
                ("position", models.CharField(max_length=128)),
                (
                    "text",
                    models.TextField(
                        blank=True,
                        max_length=500,
                        validators=[django.core.validators.MaxLengthValidator(500)],
                    ),
                ),
                ("is_title", models.BooleanField(default=False)),
                (
                    "image",
                    models.ImageField(
                        blank=True,
                        height_field="height",
                        null=True,
                        upload_to="chapters",
                        validators=[
                            core.validators.FileSizeValidator(max_megabytes=0.5)
                        ],
                        width_field="width",
                    ),
                ),
                (
                    "width",
                    models.IntegerField(
                        blank=True,
                        null=True,
                        validators=[django.core.validators.MinValueValidator(0)],
                    ),
                ),
                (
                    "height",
                    models.IntegerField(
                        blank=True,
                        null=True,
                        validators=[django.core.validators.MinValueValidator(0)],
                    ),
                ),
                (
                    "post",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="chapters",
                        to="posts.post",
                    ),
                ),
            ],
            options={
                "ordering": ["post", "position"],
                "unique_together": {("post", "position")},
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
    ]
