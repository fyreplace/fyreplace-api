# Generated by Django 3.2.12 on 2022-03-22 12:50

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("users", "0004_user_connection_token"),
    ]

    operations = [
        migrations.AddField(
            model_name="user",
            name="password",
            field=models.CharField(
                default="!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!", max_length=128
            ),
        ),
    ]