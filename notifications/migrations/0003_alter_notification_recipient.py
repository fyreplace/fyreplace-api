import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
        ("notifications", "0002_countunit_notification"),
    ]

    operations = [
        migrations.AlterField(
            model_name="notification",
            name="recipient",
            field=models.ForeignKey(
                null=True,
                on_delete=django.db.models.deletion.CASCADE,
                related_name="%(class)ss",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
    ]
