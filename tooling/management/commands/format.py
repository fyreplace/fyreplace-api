from glob import glob
from os.path import dirname, join
from pathlib import Path

import isort
from black import FileMode, Report, WriteBack, reformat_many
from django.core.management.base import BaseCommand
from isort.wrap_modes import WrapModes
from tqdm import tqdm


class Command(BaseCommand):
    def handle(self, *args, **options):
        management_dir = dirname(dirname(__file__))
        project_dir = dirname(dirname(management_dir))
        files = glob(join(project_dir, "**", "*.py"), recursive=True)
        config = isort.Config(
            quiet=True,
            multi_line_output=WrapModes.VERTICAL_HANGING_INDENT,
        )

        for file in tqdm(files):
            isort.file(file, config=config)

        reformat_many(
            sources=set([Path(f) for f in files]),
            fast=True,
            write_back=WriteBack.YES,
            mode=FileMode(),
            report=Report(quiet=True),
            workers=None,
        )
