from typing import Type

from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from django.core.validators import validate_email
from django.db import transaction
from django.db.models import Model, QuerySet
from rest_framework.decorators import action
from rest_framework.mixins import (
    CreateModelMixin,
    DestroyModelMixin,
    RetrieveModelMixin,
    UpdateModelMixin,
)
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.status import HTTP_200_OK, HTTP_205_RESET_CONTENT

from core.mixins import ClearModelMixin
from core.pagination import LimitOffsetPagination
from core.viewsets import GenericViewSet
from flags.views import FlagMixin

from .authentication import BasicAuthentication, JWTAuthentication
from .models import Token
from .permissions import (
    CurrentUserIsActive,
    CurrentUserIsPending,
    ObjectIsCurrentUser,
    ObjectIsNotCurrentUser,
)
from .serializers import (
    AuthorSerializer,
    CreateUserSerializer,
    TokenSerializer,
    UserSerializer,
)
from .tasks import send_user_email_confirmation_email, send_user_recovery_email


class BaseUserViewSet(GenericViewSet):
    class Meta:
        abstract = True

    def get_object(self) -> Type[Model]:
        lookup_url_kwarg = self.lookup_url_kwarg or self.lookup_field

        if self.kwargs[lookup_url_kwarg] == "me":
            self.kwargs[lookup_url_kwarg] = str(self.request.user.id)

        return super().get_object()


class UserViewSet(
    RetrieveModelMixin,
    UpdateModelMixin,
    DestroyModelMixin,
    BaseUserViewSet,
):
    permission_classes = BaseUserViewSet.permission_classes + [ObjectIsCurrentUser]
    queryset = get_user_model().objects.all()
    serializer_class = UserSerializer

    @action(methods=["POST"], detail=True)
    def password_change(self, request: Request, pk: str) -> Response:
        user = self.get_object()
        password = request.data.get("password")
        validate_password(password)
        user.set_password(password)
        user.save()
        serializer = self.get_serializer(user)
        return Response(status=HTTP_200_OK, data=serializer.data)

    @action(methods=["POST"], detail=True)
    def email_change(self, request: Request, pk: str) -> Response:
        user = self.get_object()
        email = request.data.get("email")
        validate_email(email)
        send_user_email_confirmation_email.delay(user_id=str(user.id), email=email)
        return Response(status=HTTP_200_OK)


class UserInteractionViewSet(FlagMixin, BaseUserViewSet):
    permission_classes = BaseUserViewSet.permission_classes + [ObjectIsNotCurrentUser]
    queryset = get_user_model().objects.all()
    serializer_class = AuthorSerializer
    pagination_class = LimitOffsetPagination

    @action(methods=["GET"], detail=False)
    def blocked(self, request: Request) -> Response:
        queryset = request.user.blocked_users.all()
        page = self.paginate_queryset(queryset)
        serializer = self.get_serializer(instance=page, many=True)
        return self.get_paginated_response(serializer.data)

    @action(methods=["POST", "PUT"], detail=True)
    def block(self, request: Request, pk: str) -> Response:
        request.user.blocked_users.add(self.get_object())
        request.user.stack.drain()
        return Response(status=HTTP_205_RESET_CONTENT)

    @block.mapping.delete
    def block_destroy(self, request: Request, pk: str) -> Response:
        request.user.blocked_users.remove(self.get_object())
        return Response(status=HTTP_200_OK)


class AccountViewSet(BaseUserViewSet):
    authentication_classes = [JWTAuthentication]
    permission_classes = [CurrentUserIsActive, ObjectIsCurrentUser]
    queryset = get_user_model().objects.all()
    serializer_class = UserSerializer

    @action(
        methods=["POST"],
        detail=True,
        permission_classes=[CurrentUserIsPending, ObjectIsCurrentUser],
    )
    @transaction.atomic
    def activation(self, request: Request, pk: str) -> Response:
        user = self.get_object()
        user.is_active = True
        user.save()
        serializer = TokenSerializer(data={}, context=self.get_serializer_context())
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response(status=HTTP_200_OK, data=serializer.data)

    @action(methods=["POST"], detail=True)
    def email_confirmation(self, request: Request, pk: str) -> Response:
        user = self.get_object()
        email = request.auth.get("email")
        validate_email(email)
        user.email = email
        user.save()
        serializer = self.get_serializer(user)
        return Response(status=HTTP_200_OK, data=serializer.data)


class SetupViewSet(CreateModelMixin, BaseUserViewSet):
    authentication_classes = []
    permission_classes = []
    queryset = get_user_model().objects.all()
    serializer_class = CreateUserSerializer

    @action(methods=["POST"], detail=False)
    def recovery(self, request: Request) -> Response:
        email = request.data.get("email")
        validate_email(email)
        send_user_recovery_email.delay(email=email)
        return Response(status=HTTP_200_OK)


class LoginViewSet(CreateModelMixin, GenericViewSet):
    authentication_classes = [BasicAuthentication, JWTAuthentication]
    queryset = Token.objects.all()
    serializer_class = TokenSerializer


class LogoutViewSet(DestroyModelMixin, ClearModelMixin, GenericViewSet):
    queryset = Token.objects.all()
    serializer_class = TokenSerializer

    def get_queryset(self) -> QuerySet:
        return super().get_queryset().filter(user=self.request.user)
