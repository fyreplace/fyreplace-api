from typing import Type

from django.db.models import Model
from rest_framework import viewsets

from .exceptions import Gone


class GenericViewSet(viewsets.GenericViewSet):
    def get_object(self) -> Type[Model]:
        obj = super().get_object()

        if getattr(obj, "is_deleted", False):
            raise Gone(f"{self.queryset.model.__name__} deleted.")

        return obj


class ModelViewSet(GenericViewSet, viewsets.ModelViewSet):
    pass
