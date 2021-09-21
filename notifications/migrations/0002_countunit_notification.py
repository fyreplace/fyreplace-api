import uuid

import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models

import core.models


class Migration(migrations.Migration):

    initial = True

    dependencies = [
        ("notifications", "0001_initial"),
    ]

    operations = [
        migrations.DeleteModel("Notification"),
        migrations.DeleteModel("CountUnit"),
        migrations.CreateModel(
            name="Notification",
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
                ("target_id", models.CharField(max_length=36)),
                ("date_updated", models.DateTimeField(auto_now=True)),
                (
                    "recipient",
                    models.ForeignKey(
                        null=True,
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="notifications",
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
                "ordering": ["date_updated", "id"],
                "unique_together": {("recipient", "target_type", "target_id")},
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
        migrations.CreateModel(
            name="CountUnit",
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
                ("count_item_id", models.CharField(max_length=36)),
                (
                    "count_item_type",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="+",
                        to="contenttypes.contenttype",
                    ),
                ),
                (
                    "notification",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="count_units",
                        to="notifications.notification",
                    ),
                ),
            ],
            options={
                "unique_together": {
                    ("notification", "count_item_type", "count_item_id")
                },
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
    ]
