from django.apps.registry import Apps
from django.db import migrations, models


def update_subscriptions(apps: Apps, *args, **kwargs):
    Subscription = apps.get_model("posts", "Subscription")
    Post = apps.get_model("posts", "Post")
    Comment = apps.get_model("posts", "Comment")

    Subscription.objects.filter(last_comment_seen__isnull=False).update(
        date_last_seen=Comment.objects.filter(
            id=models.OuterRef("last_comment_seen_id")
        ).values("date_created")[:1]
    )
    Subscription.objects.filter(last_comment_seen__isnull=True).update(
        date_last_seen=Post.objects.filter(id=models.OuterRef("post_id")).values(
            "date_published"
        )[:1]
    )


def noop(*args, **kwargs):
    pass


class Migration(migrations.Migration):

    dependencies = [
        ("posts", "0005_alter_comment_text"),
    ]

    operations = [
        migrations.AddField(
            model_name="subscription",
            name="date_last_seen",
            field=models.DateTimeField(auto_now=True, null=True),
        ),
        migrations.RunPython(update_subscriptions, noop),
        migrations.AlterField(
            model_name="subscription",
            name="date_last_seen",
            field=models.DateTimeField(auto_now=True),
        ),
    ]
