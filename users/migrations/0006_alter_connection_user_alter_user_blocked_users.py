import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("users", "0005_user_password"),
    ]

    operations = [
        migrations.AlterField(
            model_name="connection",
            name="user",
            field=models.ForeignKey(
                on_delete=django.db.models.deletion.CASCADE,
                related_name="%(class)ss",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AlterField(
            model_name="user",
            name="blocked_users",
            field=models.ManyToManyField(
                related_name="blocking_%(class)ss",
                through="users.Block",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
    ]
