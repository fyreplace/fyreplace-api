import django.core.validators
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("posts", "0003_alter_chapter_post_alter_comment_post_and_more"),
    ]

    operations = [
        migrations.AlterField(
            model_name="chapter",
            name="text",
            field=models.CharField(
                blank=True,
                max_length=500,
                validators=[django.core.validators.MaxLengthValidator(500)],
            ),
        ),
        migrations.AlterField(
            model_name="comment",
            name="text",
            field=models.CharField(
                max_length=500,
                validators=[django.core.validators.MaxLengthValidator(500)],
            ),
        ),
    ]
