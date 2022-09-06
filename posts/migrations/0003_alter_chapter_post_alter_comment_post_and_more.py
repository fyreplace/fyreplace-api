import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
        ("users", "0006_alter_connection_user_alter_user_blocked_users"),
        ("posts", "0002_initial"),
    ]

    operations = [
        migrations.AlterField(
            model_name="chapter",
            name="post",
            field=models.ForeignKey(
                on_delete=django.db.models.deletion.CASCADE,
                related_name="%(class)ss",
                to="posts.post",
            ),
        ),
        migrations.AlterField(
            model_name="comment",
            name="post",
            field=models.ForeignKey(
                on_delete=django.db.models.deletion.CASCADE,
                related_name="%(class)ss",
                to="posts.post",
            ),
        ),
        migrations.AlterField(
            model_name="post",
            name="author",
            field=models.ForeignKey(
                on_delete=django.db.models.deletion.CASCADE,
                related_name="%(class)ss",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AlterField(
            model_name="post",
            name="subscribers",
            field=models.ManyToManyField(
                related_name="subscribed_%(class)ss",
                through="posts.Subscription",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AlterField(
            model_name="post",
            name="voters",
            field=models.ManyToManyField(
                related_name="voted_%(class)ss",
                through="posts.Vote",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AlterField(
            model_name="stack",
            name="posts",
            field=models.ManyToManyField(
                related_name="+", through="posts.Visibility", to="posts.post"
            ),
        ),
        migrations.AlterField(
            model_name="stack",
            name="user",
            field=models.OneToOneField(
                on_delete=django.db.models.deletion.CASCADE,
                related_name="%(class)s",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
        migrations.AlterField(
            model_name="subscription",
            name="post",
            field=models.ForeignKey(
                on_delete=django.db.models.deletion.CASCADE,
                related_name="%(class)ss",
                to="posts.post",
            ),
        ),
        migrations.AlterField(
            model_name="subscription",
            name="user",
            field=models.ForeignKey(
                on_delete=django.db.models.deletion.CASCADE,
                related_name="%(class)ss",
                to=settings.AUTH_USER_MODEL,
            ),
        ),
    ]
