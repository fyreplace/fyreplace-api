from typing import List

from django.conf import settings
from django.core.mail import send_mail
from django.template.loader import render_to_string


class Email:
    @property
    def template(self) -> str:
        raise NotImplementedError

    @property
    def context(self) -> dict:
        raise NotImplementedError

    @property
    def subject(self) -> str:
        raise NotImplementedError

    @property
    def recipients(self) -> List[str]:
        raise NotImplementedError

    def send(self):
        text_message = render_to_string(f"{self.template}.txt", context=self.context)
        html_message = render_to_string(f"{self.template}.html", context=self.context)
        send_mail(
            subject=self.subject,
            message=text_message,
            html_message=html_message,
            from_email=settings.DEFAULT_FROM_EMAIL,
            recipient_list=self.recipients,
        )
