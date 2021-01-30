import os
import subprocess
from glob import glob
from os.path import dirname, join

from django.core.management.base import BaseCommand, CommandParser
from tqdm import tqdm


class Command(BaseCommand):
    def add_arguments(self, parser: CommandParser):
        parser.add_argument("--upgrade", action="store_true")

    def handle(self, *args, **kwargs):
        management_dir = dirname(dirname(__file__))
        project_dir = dirname(dirname(management_dir))
        os.chdir(project_dir)
        requirements_dir = "requirements"
        base_file = join(requirements_dir, "base.in")
        with_files = glob(join(requirements_dir, "with-*.in"), recursive=True)

        for file in tqdm([base_file, *with_files]):
            args = [
                "python",
                "-m",
                "piptools",
                "compile",
                "--generate-hashes",
                "--quiet",
                file,
            ]

            if kwargs["upgrade"]:
                args.append("--upgrade")

            subprocess.call(args)
