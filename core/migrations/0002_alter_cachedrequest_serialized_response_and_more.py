from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("core", "0001_initial"),
    ]

    operations = [
        migrations.AlterField(
            model_name="cachedrequest",
            name="serialized_response",
            field=models.TextField(),
        ),
        migrations.AlterField(
            model_name="cachedrequest",
            name="serialized_response_message",
            field=models.BinaryField(max_length=1024),
        ),
    ]
