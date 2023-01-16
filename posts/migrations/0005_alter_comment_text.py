import django.core.validators
from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [
        ("posts", "0004_alter_chapter_text_alter_comment_text"),
    ]

    operations = [
        migrations.AlterField(
            model_name="comment",
            name="text",
            field=models.CharField(
                max_length=1500,
                validators=[django.core.validators.MaxLengthValidator(1500)],
            ),
        ),
    ]
