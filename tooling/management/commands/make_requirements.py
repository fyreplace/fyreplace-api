import os
import subprocess
from os.path import dirname, join

from django.core.management.base import BaseCommand, CommandParser


class Command(BaseCommand):
    def add_arguments(self, parser: CommandParser):
        parser.add_argument("--upgrade", action="store_true")

    def handle(self, *args, **kwargs):
        management_dir = dirname(dirname(__file__))
        project_dir = dirname(dirname(management_dir))
        os.chdir(project_dir)
        requirements = join(project_dir, "requirements.in")
        args = [
            "python",
            "-m",
            "piptools",
            "compile",
            "--generate-hashes",
            "--quiet",
            requirements,
        ]

        if kwargs["upgrade"]:
            args.append("--upgrade")

        subprocess.call(args)
