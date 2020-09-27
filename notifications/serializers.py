from typing import Union

from rest_framework import serializers

from posts.serializers import CommentSerializer, PostSerializer

from .models import Importance, Notification


class NotificationSerializer(serializers.ModelSerializer):
    class Meta:
        model = Notification
        fields = ["id", "post", "comments_count"]

    post = PostSerializer()
    comments_count = serializers.IntegerField(source="comments.count")

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.context["preview"] = True

    def to_representation(self, instance: Union[Notification, dict]) -> dict:
        representation = super().to_representation(instance)

        if isinstance(instance, dict):
            return representation

        if instance.user == instance.post.author:
            representation["importance"] = Importance.COMMENT_OWN_POST.value

        return representation


class NewNotificationSerializer(NotificationSerializer):
    class Meta(NotificationSerializer.Meta):
        fields = NotificationSerializer.Meta.fields + ["latest_comment"]

    latest_comment = CommentSerializer(source="comments.last")
