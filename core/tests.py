from os import path
from typing import Iterable

import pytest
from django.core import mail
from rest_framework.test import APITestCase

from .emails import Email


def get_asset(name: str) -> str:
    return path.join(path.dirname(__file__), "..", "assets", name)


class PytestTestRunner:
    def __init__(self, verbosity=1, failfast=False, keepdb=False, **kwargs):
        self.verbosity = verbosity
        self.failfast = failfast
        self.keepdb = keepdb

    def run_tests(self, test_labels: Iterable[str], **kwargs) -> int:
        argv = []

        if self.verbosity == 0:
            argv.append("--quiet")
        elif self.verbosity == 2:
            argv.append("--verbose")
        elif self.verbosity == 3:
            argv.append("-vv")

        if self.failfast:
            argv.append("--exitfirst")

        if self.keepdb:
            argv.append("--reuse-db")

        argv.extend(test_labels)
        return pytest.main(argv)


class BaseAPITestCase(APITestCase):
    def assertEmails(self, emails: list[Email]):
        self.assertEqual(len(mail.outbox), len(emails))

        for i in range(0, len(emails)):
            self.assertEqual(emails[i].subject, mail.outbox[i].subject)
