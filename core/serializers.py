from typing import Union

from rest_framework import serializers

from .models import SoftDeleteModel


class TimestampModelSerializer(serializers.ModelSerializer):
    class Meta:
        abstract = True
        fields = ["date_created"]

    date_created = serializers.ReadOnlyField()


class SoftDeleteModelSerializer(serializers.ModelSerializer):
    class Meta:
        abstract = True
        fields = []

    is_deleted = serializers.ReadOnlyField()

    def to_representation(self, instance: Union[SoftDeleteModel, dict]) -> dict:
        representation = super().to_representation(instance)

        if isinstance(instance, dict):
            return representation

        if instance.is_deleted:
            representation["is_deleted"] = True

        return representation
