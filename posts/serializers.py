from typing import Union

from django.db import transaction
from django.db.models import F
from rest_framework import serializers
from rest_framework.generics import get_object_or_404

from core.serializers import SoftDeleteModelSerializer, TimestampModelSerializer
from users.serializers import AuthorSerializer

from .models import Chunk, Comment, Post, Vote


class ChunkSerializer(serializers.ModelSerializer):
    class Meta:
        model = Chunk
        fields = [
            "id",
            "position",
            "text",
            "is_title",
            "image",
        ]
        extra_kwargs = {
            "id": {"required": False, "read_only": False},
            "position": {"required": False},
            "text": {"required": False},
            "is_title": {"required": False},
            "image": {"required": False},
        }

    def validate(self, attrs: dict) -> dict:
        has_id = "id" in attrs
        has_position = "position" in attrs
        has_text = "text" in attrs
        has_image = "image" in attrs

        if has_text and has_image:
            raise serializers.ValidationError("Chunk has multiple content types.")
        elif not (has_id or has_position or has_text or has_image):
            raise serializers.ValidationError("Chunk has no data.")
        elif has_image != bool(getattr(self.instance, "image", has_image)):
            raise serializers.ValidationError("Chunk is changing its content type.")

        return super().validate(attrs)

    def create(self, validated_data: dict) -> Chunk:
        data = validated_data.copy()
        data.pop("id", None)
        post_id = data.get("post_id")

        if Post.objects.get(id=post_id).chunks.count() == Post.MAX_CHUNKS:
            raise serializers.ValidationError(
                f"Post already has the maximum number of {Post.MAX_CHUNKS} chunks."
            )

        data.setdefault("position", Chunk.objects.filter(post_id=post_id).count())
        return super().create(data)

    def to_representation(self, instance: Union[Chunk, dict]) -> dict:
        representation = super().to_representation(instance)

        if isinstance(instance, dict):
            return representation

        if isinstance(self.parent, serializers.ListSerializer):
            del representation["position"]

        useless_fields = []

        if instance.text is None:
            useless_fields.append("text")

        if not instance.is_title:
            useless_fields.append("is_title")

        if instance.image:
            representation["width"] = instance.width
            representation["height"] = instance.height
        else:
            useless_fields.append("image")

        for field in useless_fields:
            del representation[field]

        return representation


class PostSerializer(TimestampModelSerializer, SoftDeleteModelSerializer):
    class Meta(TimestampModelSerializer.Meta, SoftDeleteModelSerializer.Meta):
        model = Post
        fields = (
            TimestampModelSerializer.Meta.fields
            + SoftDeleteModelSerializer.Meta.fields
            + ["id", "author", "chunks"]
        )

    author = serializers.HiddenField(default=serializers.CurrentUserDefault())
    chunks = ChunkSerializer(many=True, required=False)

    def create(self, validated_data):
        data = validated_data.copy()
        chunks_data = data.pop("chunks", [])

        with transaction.atomic():
            post = super().create(data)

            for i, chunk_data in enumerate(chunks_data):
                chunk_data["position"] = i
                serializer = ChunkSerializer(data=chunk_data)
                serializer.is_valid(raise_exception=True)
                serializer.save(post_id=post.id)

            return post

    def update(self, instance: Post, validated_data: dict) -> Post:
        data = validated_data.copy()
        chunks_data = data.pop("chunks", [])
        chunk_ids_to_remove = list(instance.chunks.values_list("id", flat=True))

        with transaction.atomic():
            Chunk.objects.filter(post_id=instance.id).update(
                position=F("position") + Post.MAX_CHUNKS
            )

            for i, chunk_data in enumerate(chunks_data):
                chunk = (
                    get_object_or_404(Chunk, id=chunk_data["id"], post=instance)
                    if "id" in chunk_data
                    else None
                )

                chunk_data["position"] = i
                serializer = ChunkSerializer(instance=chunk, data=chunk_data)
                serializer.is_valid(raise_exception=True)
                serializer.save(post_id=instance.id)

                if serializer.instance.id in chunk_ids_to_remove:
                    chunk_ids_to_remove.remove(serializer.instance.id)

            Chunk.objects.filter(id__in=chunk_ids_to_remove).delete()
            return super().update(instance, data)

    def to_representation(self, instance: Union[Post, dict]) -> dict:
        representation = super().to_representation(instance)

        if isinstance(instance, dict):
            return representation

        is_preview = self.context.get(
            "preview", isinstance(self.parent, serializers.ListSerializer)
        )

        if instance.is_deleted:
            del representation["chunks"]
        elif is_preview:
            representation["chunks"] = representation["chunks"][:1]

        if not instance.is_anonymous:
            serializer = AuthorSerializer()
            representation["author"] = serializer.to_representation(instance.author)

        if instance.date_published is not None:
            representation["date_created"] = instance.date_published

        if (
            not is_preview
            and instance.subscribers.filter(id=self.context["request"].user.id).exists()
        ):
            representation["is_subscribed"] = True

        return representation


class VoteSerializer(serializers.ModelSerializer):
    class Meta:
        model = Vote
        fields = ["id", "user", "spread"]

    user = serializers.HiddenField(default=serializers.CurrentUserDefault())


class CommentSerializer(TimestampModelSerializer, SoftDeleteModelSerializer):
    class Meta(TimestampModelSerializer.Meta, SoftDeleteModelSerializer.Meta):
        model = Comment
        fields = (
            TimestampModelSerializer.Meta.fields
            + SoftDeleteModelSerializer.Meta.fields
            + ["id", "author", "text"]
        )

    author = serializers.HiddenField(default=serializers.CurrentUserDefault())

    def to_representation(self, instance: Union[Comment, dict]) -> dict:
        representation = super().to_representation(instance)

        if isinstance(instance, dict):
            return representation

        is_blocked = (
            self.context["request"]
            .user.blocked_users.filter(id=instance.author.id)
            .exists()
        )

        if instance.is_deleted or is_blocked:
            del representation["text"]

        if is_blocked:
            representation["is_blocked"] = True
        else:
            serializer = AuthorSerializer()
            representation["author"] = serializer.to_representation(instance.author)

        return representation
