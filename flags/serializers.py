from rest_framework import serializers

from .models import Flag


class FlagSerializer(serializers.ModelSerializer):
    class Meta:
        model = Flag
        fields = ["issuer", "comment"]

    issuer = serializers.HiddenField(
        default=serializers.CurrentUserDefault(), write_only=True
    )
