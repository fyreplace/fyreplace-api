import uuid

import django.db.models.deletion
from django.db import migrations, models

import core.models


class Migration(migrations.Migration):

    dependencies = [
        ("users", "0006_alter_connection_user_alter_user_blocked_users"),
        ("notifications", "0005_flag_alter_notification_unique_together_and_more"),
    ]

    operations = [
        migrations.CreateModel(
            name="ApnsToken",
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
