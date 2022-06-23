import io
import uuid
from inspect import isclass
from typing import Any, Callable, Iterator, Optional, Union

import magic
from django.conf import settings
from django.core.files.images import ImageFile
from django.db import models
from grpc_interceptor.exceptions import InvalidArgument

from protos import image_pb2

MaybeImageChunk = Union[image_pb2.ImageChunk, Any]


def get_servicer_interfaces(cls: type) -> list[type]:
    return (
        [s for s in cls.mro() if s != cls and s.__name__.endswith("Servicer")]
        if isclass(cls)
        else []
    )


class ImageUploadMixin:
    def get_image(
        self,
        request_iterator: Iterator,
        chunkator: Optional[Callable[[MaybeImageChunk], MaybeImageChunk]] = None,
    ) -> Optional[ImageFile]:
        data = bytes()
        type_checked = False
        extension = ""

        def validate_type():
            nonlocal extension
            mime = magic.from_buffer(data, mime=True)

            if mime in settings.VALID_IMAGE_MIMES:
                extension = mime.split("/")[-1]
            else:
                raise InvalidArgument("invalid_file_type")

        for request in request_iterator:
            data += chunkator(request).data if chunkator else request.data
            size = len(data)

            if size >= 2048 and not type_checked:
                validate_type()
                type_checked = True
            if size > settings.FILE_UPLOAD_MAX_MEMORY_SIZE:
                raise InvalidArgument("payload_too_large")

        if data and not type_checked:
            validate_type()

        return (
            ImageFile(io.BytesIO(data), name=f"{uuid.uuid4()}.{extension}")
            if data
            else None
        )

    def set_image(
        self, model: type[models.Model], field: str, image: Optional[ImageFile]
    ):
        getattr(model, field).delete()
        setattr(model, field, image)
        model.full_clean()
        model.save()
