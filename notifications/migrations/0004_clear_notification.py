from django.apps.registry import Apps
from django.db import migrations


def delete_notifications(apps: Apps, *args, **kwargs):
    apps.get_model("notifications", "Notification").objects.all().delete()


def noop(*args, **kwargs):
    pass


class Migration(migrations.Migration):

    dependencies = [
        ("notifications", "0003_alter_notification_recipient"),
    ]

    operations = [
        migrations.RunPython(delete_notifications, noop),
    ]
