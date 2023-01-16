import django.contrib.auth.validators
import django.core.validators
from django.db import migrations, models

import users.validators


class Migration(migrations.Migration):
    dependencies = [
        ("users", "0007_alter_user_bio"),
    ]

    operations = [
        migrations.AlterField(
            model_name="user",
            name="bio",
            field=models.CharField(blank=True, max_length=3000),
        ),
        migrations.AlterField(
            model_name="user",
            name="username",
            field=models.CharField(
                max_length=50,
                null=True,
                unique=True,
                validators=[
                    django.contrib.auth.validators.UnicodeUsernameValidator(),
                    django.core.validators.MinLengthValidator(3),
                    users.validators.UsernameNotReservedValidator(),
                ],
            ),
        ),
    ]
