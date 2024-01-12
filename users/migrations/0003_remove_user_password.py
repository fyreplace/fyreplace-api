from django.db import migrations


class Migration(migrations.Migration):
    dependencies = [
        ("users", "0002_initial"),
    ]

    operations = [
        migrations.RemoveField(
            model_name="user",
            name="password",
        ),
    ]
