#!/usr/bin/env python

import os
import sys


def main():
    settings_package = "core.settings"

    if "test" in sys.argv:
        settings_package += ".testing"

    os.environ.setdefault("DJANGO_SETTINGS_MODULE", settings_package)

    try:
        from django.core.management import execute_from_command_line
    except ImportError as exc:
        raise ImportError(
            "Couldn't import Django.\n"
            "Are you sure it's installed and available on your PYTHONPATH environment variable?\n"
            "Did you forget to activate a virtual environment?"
        ) from exc
    execute_from_command_line(sys.argv)


if __name__ == "__main__":
    main()
