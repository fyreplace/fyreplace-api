from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("users", "0003_remove_user_password"),
    ]

    operations = [
        migrations.AddField(
            model_name="user",
            name="connection_token",
            field=models.UUIDField(blank=True, null=True),
        ),
    ]
