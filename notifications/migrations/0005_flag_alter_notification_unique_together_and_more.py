import uuid

import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models

import core.models


class Migration(migrations.Migration):
    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
        ("contenttypes", "0002_remove_content_type_name"),
        ("posts", "0003_alter_chapter_post_alter_comment_post_and_more"),
        ("notifications", "0004_clear_notification"),
    ]

    operations = [
        migrations.CreateModel(
            name="Flag",
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
        migrations.AddField(
            model_name="notification",
            name="count",
            field=models.IntegerField(default=0),
        ),
        migrations.AddField(
            model_name="notification",
            name="subscription",
            field=models.OneToOneField(
                null=True,
                on_delete=django.db.models.deletion.CASCADE,
                related_name="+",
                to="posts.subscription",
            ),
        ),
        migrations.AlterUniqueTogether(
            name="notification",
            unique_together={("subscription", "target_type", "target_id")},
        ),
        migrations.DeleteModel(
            name="CountUnit",
        ),
        migrations.RemoveField(
            model_name="notification",
            name="recipient",
        ),
    ]
