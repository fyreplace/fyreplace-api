import signal

from django.conf import settings
from django.core.management.base import BaseCommand
from django.utils import autoreload

from core.grpc import create_server


class Command(BaseCommand):
    def handle(self, *args, **kwargs):
        if settings.DEBUG:
            autoreload.run_with_reloader(self.run, **kwargs)
        else:
            self.run()

    def run(self, *args, **kwargs):
        autoreload.raise_last_exception()

        if not settings.DEBUG:
            for sig in [signal.SIGHUP, signal.SIGINT, signal.SIGTERM]:
                signal.signal(sig, lambda: self.stop_server())

        self.run_server()

    def run_server(self):
        self.server = create_server()

        try:
            print("gRPC server starting...")
            self.server.start()
            print("gRPC server started")
            self.server.wait_for_termination()
        finally:
            print("gRPC server stopping...")
            self.stop_server()
            print("gRPC server stopped")

    def stop_server(self, *args, **kwargs):
        self.server.stop(grace=10)
