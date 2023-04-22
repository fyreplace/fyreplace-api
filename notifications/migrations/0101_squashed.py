import uuid

import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models

import core.models


class Migration(migrations.Migration):
    replaces = [
        ("notifications", "0001_initial"),
        ("notifications", "0002_countunit_notification"),
        ("notifications", "0003_alter_notification_recipient"),
        ("notifications", "0004_clear_notification"),
        ("notifications", "0005_flag_alter_notification_unique_together_and_more"),
        ("notifications", "0006_apnstoken_remotemessaging"),
        ("notifications", "0007_alter_id"),
    ]

    initial = True

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
        ("contenttypes", "0002_remove_content_type_name"),
        ("users", "0101_squashed"),
        ("posts", "0101_squashed"),
    ]

    operations = [
        migrations.CreateModel(
            name="Notification",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        editable=False,
                        primary_key=True,
                        serialize=False,
                        unique=True,
                    ),
                ),
                (
                    "subscription",
                    models.OneToOneField(
                        null=True,
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="+",
                        to="posts.subscription",
                    ),
                ),
                ("target_id", models.CharField(max_length=36)),
                ("count", models.IntegerField(default=0)),
                ("date_updated", models.DateTimeField(auto_now=True)),
                (
                    "target_type",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="+",
                        to="contenttypes.contenttype",
                    ),
                ),
            ],
            options={
                "ordering": ["date_updated", "id"],
                "unique_together": {("subscription", "target_type", "target_id")},
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
        migrations.CreateModel(
            name="Flag",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        editable=False,
                        primary_key=True,
                        serialize=False,
                        unique=True,
                    ),
                ),
                ("target_id", models.CharField(max_length=36)),
                (
                    "issuer",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="+",
                        to=settings.AUTH_USER_MODEL,
                    ),
                ),
                (
                    "target_type",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="+",
                        to="contenttypes.contenttype",
                    ),
                ),
            ],
            options={
                "unique_together": {("issuer", "target_type", "target_id")},
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
        migrations.CreateModel(
            name="ApnsToken",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        editable=False,
                        primary_key=True,
                        serialize=False,
                        unique=True,
                    ),
                ),
                ("date_created", models.DateTimeField(auto_now_add=True)),
                ("token", models.CharField(max_length=300)),
            ],
            options={
                "ordering": ["date_created", "id"],
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
        migrations.CreateModel(
            name="RemoteMessaging",
            fields=[
                (
                    "id",
                    models.UUIDField(
                        default=uuid.uuid4,
                        editable=False,
                        primary_key=True,
                        serialize=False,
                        unique=True,
                    ),
                ),
                ("service", models.IntegerField(choices=[(1, "Apns"), (2, "Fcm")])),
                ("token", models.CharField(max_length=300, unique=True)),
                (
                    "connection",
                    models.OneToOneField(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="messaging",
                        to="users.connection",
                    ),
                ),
            ],
            options={
                "abstract": False,
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
    ]
