import os
from uuid import uuid4

from django.core.files.storage import FileSystemStorage as BaseStorage


class FileSystemStorage(BaseStorage):
    def get_available_name(self, name: str, max_length: int = None) -> str:
        dir_name, file_name = os.path.split(name)
        _, file_ext = os.path.splitext(file_name)
        return os.path.join(dir_name, f"{uuid4()}{file_ext}")
