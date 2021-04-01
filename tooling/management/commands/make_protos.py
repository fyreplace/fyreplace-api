import os
import subprocess
from glob import glob
from os.path import dirname, join

from django.core.management.base import BaseCommand
from tqdm import tqdm


class Command(BaseCommand):
    def handle(self, *args, **kwargs):
        self.generate_protos(progress=True)

    def generate_protos(self, progress: bool = False):
        management_dir = dirname(dirname(__file__))
        project_dir = dirname(dirname(management_dir))
        os.chdir(project_dir)
        files = glob(join("protos", "*.proto"))

        for file in tqdm(files) if progress else files:
            self.generate_proto(file)

    def generate_proto(self, file: str):
        subprocess.call(
            [
                "python",
                "-m",
                "grpc_tools.protoc",
                "--proto_path=.",
                "--python_out=.",
                "--grpc_python_out=.",
                "--mypy_out=quiet:.",
                file,
            ]
        )
