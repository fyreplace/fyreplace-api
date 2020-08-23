from typing import Union

from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractUser
from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers

from core.serializers import SoftDeleteModelSerializer

from .models import Token


class AuthorSerializer(SoftDeleteModelSerializer):
    class Meta(SoftDeleteModelSerializer.Meta):
        model = get_user_model()
        fields = SoftDeleteModelSerializer.Meta.fields + ["id", "username", "avatar"]

    username = serializers.ReadOnlyField()

    def to_representation(self, instance: Union[AbstractUser, dict]) -> dict:
        representation = super().to_representation(instance)

        if isinstance(instance, dict):
            return representation

        if not instance.avatar:
            del representation["avatar"]

        if instance.is_banned:
            representation["is_banned"] = True

        return representation

    def update(self, instance: AbstractUser, validated_data: dict) -> AbstractUser:
        if "avatar" in validated_data:
            instance.avatar.delete(save=False)

        return super().update(instance, validated_data)


class UserSerializer(AuthorSerializer):
    class Meta(AuthorSerializer.Meta):
        fields = AuthorSerializer.Meta.fields + ["date_created", "bio"]

    date_created = serializers.ReadOnlyField(source="date_joined")

    def to_representation(self, instance: Union[AbstractUser, dict]) -> dict:
        representation = super().to_representation(instance)

        if isinstance(instance, dict):
            return representation

        if len(instance.bio) == 0:
            del representation["bio"]

        request = self.context.get("request")

        if request is not None and request.user.id == instance.id:
            representation["email"] = instance.email

        return representation


class CreateUserSerializer(UserSerializer):
    class Meta(UserSerializer.Meta):
        fields = UserSerializer.Meta.fields + ["is_active", "password", "email"]
        extra_kwargs = {
            "password": {"required": True, "write_only": True},
            "email": {"required": True},
        }

    username = None
    is_active = serializers.HiddenField(default=False)

    def validate_password(self, password: str) -> str:
        validate_password(password)
        return password

    def create(self, validated_data: dict) -> AbstractUser:
        return get_user_model().objects.create_user(**validated_data)

    def update(self, instance: AbstractUser, validated_data: dict) -> AbstractUser:
        raise NotImplementedError


class TokenSerializer(serializers.ModelSerializer):
    class Meta:
        model = Token
        fields = ["key", "user"]

    user = serializers.HiddenField(default=serializers.CurrentUserDefault())
