from glob import glob
from os.path import dirname, join
from pathlib import Path

import isort
from black import FileMode, WriteBack, format_file_in_place
from django.core.management.base import BaseCommand
from isort.wrap_modes import WrapModes
from tqdm import tqdm


class Command(BaseCommand):
    def handle(self, *args, **kwargs):
        management_dir = dirname(dirname(__file__))
        project_dir = dirname(dirname(management_dir))
        config = isort.Config(
            quiet=True,
            multi_line_output=WrapModes.VERTICAL_HANGING_INDENT,
        )
        mode = FileMode()

        for file in tqdm(glob(join(project_dir, "**", "*.py"), recursive=True)):
            isort.file(file, config=config)
            format_file_in_place(
                Path(file),
                fast=True,
                mode=mode,
                write_back=WriteBack.YES,
            )
