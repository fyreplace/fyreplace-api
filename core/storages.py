import os
import uuid
from typing import Optional
from urllib.parse import urljoin

from django.conf import settings
from django.core.files.storage import FileSystemStorage as BaseStorage
from django.db.models.fields.files import ImageFieldFile
from storages.backends.s3boto3 import S3Boto3Storage


def get_image_url(file: ImageFieldFile) -> str:
    url = file.url
    return urljoin(settings.BASE_URL, url) if url.startswith("/") else url


class RandomNameMixin:
    def get_available_name(self, name: str, max_length: Optional[int] = None) -> str:
        dir_name, file_name = os.path.split(name)
        _, file_ext = os.path.splitext(file_name)
        return os.path.join(dir_name, f"{uuid.uuid4()}{file_ext}")


class FileSystemStorage(RandomNameMixin, BaseStorage):
    pass


class BotoStorage(RandomNameMixin, S3Boto3Storage):
    pass
