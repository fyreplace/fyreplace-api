from datetime import datetime, timedelta

import rest_framework.authentication
from django.conf import settings
from django.contrib.auth import get_user_model
from django.core.exceptions import ValidationError
from django.db.models import Q
from jose import jwt
from jose.exceptions import JOSEError
from rest_framework import exceptions
from rest_framework.request import Request

from .models import Token


class TokenAuthentication(rest_framework.authentication.TokenAuthentication):
    model = Token


class JWTAuthentication(rest_framework.authentication.TokenAuthentication):
    keyword = "Bearer"

    def authenticate_credentials(self, key) -> tuple:
        User = get_user_model()

        try:
            claims = jwt.decode(key, key=settings.SECRET_KEY)
            timestamp = claims["timestamp"]
            token_date = datetime.utcfromtimestamp(timestamp)

            if datetime.utcnow() - token_date > timedelta(minutes=30):
                raise exceptions.AuthenticationFailed

            id_query = Q(id=claims.get("user_id"))
            email_query = Q(email=claims.get("email"))
            user = User.objects.get(id_query | email_query)
        except (KeyError, JOSEError, ValidationError, User.DoesNotExist):
            raise exceptions.AuthenticationFailed

        return (user, claims)


class BasicAuthentication(rest_framework.authentication.BasicAuthentication):
    def authenticate_credentials(
        self, userid: str, password: str, request: Request = None
    ) -> tuple:
        user = get_user_model().objects.filter(email=userid).first()
        identifier = user.username if user is not None else userid
        return super().authenticate_credentials(identifier, password, request)
