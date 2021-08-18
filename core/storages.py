import os
from typing import Optional
from uuid import uuid4

from django.conf import settings
from django.core.files.storage import FileSystemStorage as BaseStorage
from django.db.models.fields.files import ImageFieldFile


def get_image_url(file: ImageFieldFile) -> str:
    url = file.url
    return os.path.join(settings.BASE_URL, url) if url.startswith("/") else url


class FileSystemStorage(BaseStorage):
    def get_available_name(self, name: str, max_length: Optional[int] = None) -> str:
        dir_name, file_name = os.path.split(name)
        _, file_ext = os.path.splitext(file_name)
        return os.path.join(dir_name, f"{uuid4()}{file_ext}")
