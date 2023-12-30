import django.core.validators
from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("users", "0006_alter_connection_user_alter_user_blocked_users"),
    ]

    operations = [
        migrations.AlterField(
            model_name="user",
            name="bio",
            field=models.CharField(
                blank=True,
                max_length=3000,
                validators=[django.core.validators.MaxLengthValidator(3000)],
            ),
        ),
    ]
