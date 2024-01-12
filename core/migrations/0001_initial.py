import uuid

from django.db import migrations, models

import core.models


class Migration(migrations.Migration):
    initial = True

    dependencies = []

    operations = [
        migrations.CreateModel(
            name="CachedRequest",
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
                ("request_id", models.CharField(max_length=50, unique=True)),
                ("serialized_response", models.CharField(max_length=1000)),
                ("serialized_response_message", models.BinaryField(max_length=100)),
            ],
            options={
                "abstract": False,
            },
            bases=(models.Model, core.models.MessageConvertible),
        ),
    ]
