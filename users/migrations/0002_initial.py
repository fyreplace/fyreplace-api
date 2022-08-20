import uuid

import django.contrib.auth.models
import django.contrib.auth.validators
import django.core.validators
import django.db.models.deletion
import django.db.models.expressions
import django.utils.timezone
from django.conf import settings
from django.db import migrations, models

import core.models
import core.validators
import users.models


class Migration(migrations.Migration):

    initial = True

    dependencies = [
        ("users", "0001_initial"),
    ]

    operations = [
        migrations.DeleteModel("User"),
        migrations.DeleteModel("Connection"),
        migrations.DeleteModel("Block"),
        migrations.CreateModel(
            name="User",
            fields=[
                ("password", models.CharField(max_length=128, verbose_name="password")),
                (
                    "last_login",
                    models.DateTimeField(
                        blank=True, null=True, verbose_name="last login"
                    ),
                ),
                (
                    "is_superuser",
                    models.BooleanField(
                        default=False,
                        help_text="Designates that this user has all permissions without explicitly assigning them.",
                        verbose_name="superuser status",
                    ),
                ),
                (
                    "first_name",
                    models.CharField(
                        blank=True, max_length=150, verbose_name="first name"
                    ),
                ),
                (
                    "last_name",
                    models.CharField(
                        blank=True, max_length=150, verbose_name="last name"
                    ),
                ),
                (
                    "is_staff",
                    models.BooleanField(
                        default=False,
                        help_text="Designates whether the user can log into this admin site.",
                        verbose_name="staff status",
                    ),
                ),
                (
                    "is_active",
                    models.BooleanField(
                        default=True,
                        help_text="Designates whether this user should be treated as active. Unselect this instead of deleting accounts.",
                        verbose_name="active",
                    ),
                ),
                (
                    "date_joined",
                    models.DateTimeField(
                        default=django.utils.timezone.now, verbose_name="date joined"
                    ),
                ),
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        primary_key=True,
                        serialize=False,
                        unique=True,
                    ),
                ),
                ("is_deleted", models.BooleanField(default=False)),
                (
                    "username",
                    models.CharField(
                        max_length=50,
                        null=True,
                        unique=True,
                        validators=[
                            django.contrib.auth.validators.UnicodeUsernameValidator(),
                            django.core.validators.MinLengthValidator(3),
                        ],
                    ),
                ),
                ("email", models.EmailField(max_length=254, null=True, unique=True)),
                (
                    "avatar",
                    models.ImageField(
                        blank=True,
                        null=True,
                        upload_to="avatars",
                        validators=[
                            core.validators.FileSizeValidator(max_bytes=1024 * 1024)
                        ],
                    ),
                ),
                (
                    "bio",
                    models.TextField(
                        blank=True,
                        max_length=3000,
                        validators=[django.core.validators.MaxLengthValidator(3000)],
                    ),
                ),
                ("is_banned", models.BooleanField(default=False)),
                ("date_ban_end", models.DateTimeField(blank=True, null=True)),
            ],
            options={
                "ordering": ["username", "date_joined", "id"],
            },
            bases=(models.Model, core.models.MessageConvertible),
            managers=[
                ("existing_objects", users.models.ExistingUserManager()),
                ("objects", django.contrib.auth.models.UserManager()),
            ],
        ),
        migrations.CreateModel(
            name="Connection",
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
                ("date_last_used", models.DateTimeField(auto_now=True)),
                (
                    "hardware",
                    models.CharField(
                        choices=[
                            ("desktop", "Desktop"),
                            ("mobile", "Mobile"),
                            ("watch", "Watch"),
                            ("unknown", "Unknown"),
                        ],
                        default="unknown",
                        max_length=30,
                    ),
                ),
                (
                    "software",
                    models.CharField(
                        choices=[
                            ("android", "Android"),
                            ("bsd", "BSD"),
                            ("darwin", "Darwin"),
                            ("linux", "Linux"),
                            ("windows", "Windows"),
                            ("unknown", "Unknown"),
                        ],
                        default="unknown",
                        max_length=30,
                    ),
                ),
                (
                    "user",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="connections",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={
                "ordering": [
                    "date_last_used",
                    "date_created",
                    "hardware",
                    "software",
                    "id",
                ],
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
        migrations.CreateModel(
            name="Block",
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
                    "issuer",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="+",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
                (
                    "target",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="+",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
            ],
            options={
                "ordering": ["issuer", "target"],
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
        migrations.AddField(
            model_name="user",
            name="blocked_users",
            field=models.ManyToManyField(
                related_name="_users_user_blocked_users_+",
                through="users.Block",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AddField(
            model_name="user",
            name="groups",
            field=models.ManyToManyField(
                blank=True,
                help_text="The groups this user belongs to. A user will get all permissions granted to each of their groups.",
                related_name="user_set",
                related_query_name="user",
                to="auth.Group",
                verbose_name="groups",
            ),
        ),
        migrations.AddField(
            model_name="user",
            name="user_permissions",
            field=models.ManyToManyField(
                blank=True,
                help_text="Specific permissions for this user.",
                related_name="user_set",
                related_query_name="user",
                to="auth.Permission",
                verbose_name="user permissions",
            ),
        ),
        migrations.AddConstraint(
            model_name="connection",
            constraint=models.CheckConstraint(
                check=models.Q(
                    ("hardware__in", ["desktop", "mobile", "watch", "unknown"])
                ),
                name="hardware",
            ),
        ),
        migrations.AddConstraint(
            model_name="connection",
            constraint=models.CheckConstraint(
                check=models.Q(
                    (
                        "software__in",
                        ["android", "bsd", "darwin", "linux", "windows", "unknown"],
                    )
                ),
                name="software",
            ),
        ),
        migrations.AddConstraint(
            model_name="block",
            constraint=models.CheckConstraint(
                check=models.Q(
                    ("issuer", django.db.models.expressions.F("target")), _negated=True
                ),
                name="issuer_ne_target",
            ),
        ),
        migrations.AlterUniqueTogether(
            name="block",
            unique_together={("issuer", "target")},
        ),
    ]
