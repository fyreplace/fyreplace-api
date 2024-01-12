import uuid
from datetime import datetime
from importlib import import_module
from typing import Any, Optional

import grpc
from django.db import models
from django.db.models.fields.files import ImageFieldFile
from google.protobuf import empty_pb2, timestamp_pb2
from google.protobuf.message import Message

from core.storages import get_image_url
from protos import image_pb2

from .signals import post_soft_delete, pre_soft_delete


class MessageConvertible:
    default_message_class = empty_pb2.Empty
    _message_class: Optional[type[Message]] = None
    _field_message_classes: dict[str, type[Message]] = {}
    _context: Optional[grpc.ServicerContext] = None

    def get_message_fields(self, **overrides) -> list[str]:
        return self._message_class.DESCRIPTOR.fields_by_name.keys()

    def get_message_field_values(self, **overrides) -> dict:
        values = {
            field: self.convert_field(field)
            for field in self.get_message_fields(**overrides)
            if hasattr(self, field) and field not in overrides
        }

        for field in overrides:
            if field in self._message_class.DESCRIPTOR.fields_by_name:
                values[field] = overrides[field]

        return values

    def to_message(
        self,
        message_class: Optional[type[Message]] = None,
        context: Optional[grpc.ServicerContext] = None,
        **overrides,
    ) -> Message:
        old_messages_class = self._message_class
        old_context = self._context
        self._message_class = message_class or self.default_message_class
        self._context = context or self._context
        message = self._message_class(**self.get_message_field_values(**overrides))
        self._message_class = old_messages_class
        self._context = old_context
        return message

    def convert_field(self, field: str) -> Any:
        return self.convert_value(field, getattr(self, field))

    def convert_value(self, field: str, value: Any) -> Any:
        if isinstance(value, uuid.UUID):
            return value.bytes
        elif isinstance(value, datetime):
            return timestamp_pb2.Timestamp(seconds=round(value.timestamp()))
        elif isinstance(value, MessageConvertible):
            return value.to_message(
                message_class=self.retrieve_message_class(field), context=self._context
            )
        elif isinstance(value, ImageFieldFile):
            return image_pb2.Image(url=get_image_url(value)) if value else None
        else:
            return value

    def retrieve_message_class(self, field: str) -> type[Message]:
        if message_class := self.__class__._field_message_classes.get(field):
            return message_class

        field_type = self._message_class.DESCRIPTOR.fields_by_name[field].message_type
        module_name = field_type.file.name.replace(".proto", "_pb2").replace("/", ".")
        message_class = getattr(import_module(module_name), field_type.name)
        self.__class__._field_message_classes[field] = message_class
        return message_class


class UUIDModel(models.Model, MessageConvertible):
    class Meta:
        abstract = True

    id = models.UUIDField(primary_key=True, unique=True, default=uuid.uuid4)


class TimestampModel(models.Model, MessageConvertible):
    class Meta:
        abstract = True

    date_created = models.DateTimeField(auto_now_add=True)


class SoftDeleteModel(models.Model, MessageConvertible):
    class Meta:
        abstract = True

    is_deleted = models.BooleanField(default=False)

    def soft_delete(self) -> tuple[int, dict[str, int]]:
        pre_soft_delete.send(sender=self.__class__, instance=self)
        self.perform_soft_delete()
        post_soft_delete.send(sender=self.__class__, instance=self)
        return 0, {}

    def perform_soft_delete(self):
        self.is_deleted = True
        self.save()


class ExistingManager(models.Manager):
    def get_queryset(self) -> models.QuerySet:
        return super().get_queryset().filter(is_deleted=False)


class CachedRequest(UUIDModel, TimestampModel):
    request_id = models.CharField(max_length=50, unique=True)
    serialized_response = models.CharField(max_length=1000)
    serialized_response_message = models.BinaryField(max_length=100)
