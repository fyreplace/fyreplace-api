import re
import unicodedata
from datetime import timedelta
from os import path
from typing import Iterator

import grpc
from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password
from django.contrib.contenttypes.models import ContentType
from django.core.exceptions import ValidationError
from django.core.validators import validate_email
from django.db.models import Q
from django.db.transaction import atomic
from google.protobuf import empty_pb2
from grpc_interceptor.exceptions import AlreadyExists, InvalidArgument, PermissionDenied

from core import jwt
from core.authentication import no_auth
from core.grpc import get_info_from_token, get_token, serialize_message
from core.services import ImageUploadMixin
from notifications.models import delete_notifications_for
from notifications.tasks import report_content
from protos import id_pb2, image_pb2, user_pb2, user_pb2_grpc

from .models import Connection
from .tasks import (
    fetch_default_user_avatar,
    send_account_activation_email,
    send_account_recovery_email,
    send_user_email_update_email,
)
from .validators import validate_unicode_username


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore")
    return re.sub(r"[^\w]", "", value.decode("ascii")).strip().upper()


class AccountService(user_pb2_grpc.AccountServiceServicer):
    def __init__(self):
        super().__init__()
        reserved = open(path.join(__package__, "reserved-usernames.txt"), "r")
        self.reserved_usernames = [normalize(name) for name in reserved]

    @no_auth
    def Create(
        self, request: user_pb2.UserCreation, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        data = serialize_message(request)
        User = get_user_model()

        if normalize(request.username) in self.reserved_usernames:
            raise PermissionDenied("username_reserved")
        elif User.objects.filter(email=request.email).exists():
            raise AlreadyExists("email_taken")
        elif User.objects.filter(username=request.username).exists():
            raise AlreadyExists("username_taken")

        try:
            error_message = "invalid_email"
            validate_email(request.email)
            error_message = "invalid_username"
            validate_unicode_username(request.username)
            error_message = "invalid_password"
            validate_password(request.password)
        except ValidationError:
            raise InvalidArgument(error_message)

        with atomic():
            user = User.objects.create_user(**data, is_active=False)
            user.full_clean()

        user_id = str(user.id)
        send_account_activation_email.delay(user_id=user_id)
        fetch_default_user_avatar.delay(user_id=user_id)
        return empty_pb2.Empty()

    def Delete(
        self, request: empty_pb2.Empty, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        context.caller.delete()
        return empty_pb2.Empty()

    @no_auth
    def SendActivationEmail(
        self, request: user_pb2.Email, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        if user := get_user_model().objects.filter(email=request.email).first():
            if user.is_pending:
                send_account_activation_email.delay(user_id=str(user.id))

        return empty_pb2.Empty()

    @no_auth
    @atomic
    def ConfirmActivation(
        self, request: user_pb2.ConnectionToken, context: grpc.ServicerContext
    ) -> user_pb2.Token:
        user, _ = get_info_from_token(request.token, for_update=True)

        if not user.is_pending:
            raise PermissionDenied("user_not_pending")

        user.is_active = True
        user.save()
        connection = Connection.objects.create(
            user=user,
            hardware=request.client.hardware,
            software=request.client.software,
        )
        connection.full_clean()
        return user_pb2.Token(token=connection.get_token())

    @no_auth
    def SendRecoveryEmail(
        self, request: user_pb2.Email, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        try:
            validate_email(request.email)
        except ValidationError:
            raise InvalidArgument("invalid_email")

        if user := get_user_model().objects.filter(email=request.email).first():
            if user.is_alive_and_kicking:
                send_account_recovery_email.delay(user_id=str(user.id))

        return empty_pb2.Empty()

    @no_auth
    def ConfirmRecovery(
        self, request: user_pb2.ConnectionToken, context: grpc.ServicerContext
    ) -> user_pb2.Token:
        user, _ = get_info_from_token(request.token)

        if not user.is_alive_and_kicking:
            raise PermissionDenied("user_not_recoverable")

        with atomic():
            connection = Connection.objects.create(
                user=user,
                hardware=request.client.hardware,
                software=request.client.software,
            )
            connection.full_clean()

        return user_pb2.Token(token=connection.get_token())

    def ListConnections(
        self, request: empty_pb2.Empty, context: grpc.ServicerContext
    ) -> user_pb2.Connections:
        connections = Connection.objects.filter(user=context.caller)
        return user_pb2.Connections(connections=[c.to_message() for c in connections])

    @no_auth
    def Connect(
        self, request: user_pb2.Credentials, context: grpc.ServicerContext
    ) -> user_pb2.Token:
        user_query = Q(username=request.identifier) | Q(email=request.identifier)

        if not request.identifier or not (
            user := get_user_model().objects.filter(user_query).first()
        ):
            raise InvalidArgument("invalid_credentials")
        elif not user.check_password(request.password):
            raise InvalidArgument("invalid_credentials")
        elif not user.is_alive_and_kicking:
            if user.is_pending:
                message_end = "pending"
            elif user.is_deleted:
                message_end = "deleted"
            else:
                message_end = "banned"

            raise PermissionDenied("caller_" + message_end)

        with atomic():
            connection = Connection.objects.create(
                user=user,
                hardware=request.client.hardware,
                software=request.client.software,
            )
            connection.full_clean()

        return user_pb2.Token(token=connection.get_token())

    def Disconnect(
        self, request: id_pb2.StringId, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        token = get_token(context)
        user, connection = get_info_from_token(token)
        Connection.objects.get(id=request.id or connection.id, user=user).delete()
        return empty_pb2.Empty()

    def DisconnectAll(
        self, request: empty_pb2.Empty, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        Connection.objects.filter(user=context.caller).delete()
        return empty_pb2.Empty()


class UserService(ImageUploadMixin, user_pb2_grpc.UserServiceServicer):
    def Retrieve(
        self, request: id_pb2.StringId, context: grpc.ServicerContext
    ) -> user_pb2.User:
        return get_user_model().existing_objects.get(id=request.id).to_message(email="")

    def RetrieveMe(
        self, request: empty_pb2.Empty, context: grpc.ServicerContext
    ) -> user_pb2.User:
        return context.caller.to_message()

    @atomic
    def UpdateBio(
        self, request: user_pb2.Bio, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        user = get_user_model().objects.select_for_update().get(id=context.caller.id)
        user.bio = request.bio
        user.full_clean()
        user.save()
        return empty_pb2.Empty()

    @atomic
    def UpdateAvatar(
        self,
        request_iterator: Iterator[image_pb2.ImageChunk],
        context: grpc.ServicerContext,
    ) -> empty_pb2.Empty:
        image = self.get_image(str(context.caller.id), request_iterator)
        user = get_user_model().objects.select_for_update().get(id=context.caller.id)
        self.set_image(user, "avatar", image)
        return empty_pb2.Empty()

    @atomic
    def UpdatePassword(
        self, request: user_pb2.Password, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        validate_password(request.password)
        user = get_user_model().objects.select_for_update().get(id=context.caller.id)
        user.set_password(request.password)
        user.save()
        return empty_pb2.Empty()

    def SendEmailUpdateEmail(
        self, request: user_pb2.Email, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        validate_email(request.email)
        send_user_email_update_email.delay(
            user_id=str(context.caller.id), email=request.email
        )
        return empty_pb2.Empty()

    @atomic
    def ConfirmEmailUpdate(
        self, request: user_pb2.Token, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        user = get_user_model().objects.select_for_update().get(id=context.caller.id)
        request_user, _ = get_info_from_token(request.token)

        if request_user != user:
            raise PermissionDenied("invalid_user")

        user.email = jwt.decode(request.token).get("email")
        user.full_clean()
        user.save()
        return empty_pb2.Empty()

    def ListBlocked(
        self, request: empty_pb2.Empty, context: grpc.ServicerContext
    ) -> user_pb2.Profiles:
        users = context.caller.blocked_users.all()
        return user_pb2.Profiles(
            profiles=[u.to_message(message_class=user_pb2.Profile) for u in users]
        )

    def UpdateBlock(
        self, request: user_pb2.Block, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        user = get_user_model().existing_objects.get(id=request.id)

        if request.blocked:
            context.caller.blocked_users.add(user)
        else:
            context.caller.blocked_users.remove(user)

        return empty_pb2.Empty()

    def Report(
        self, request: id_pb2.StringId, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        user = get_user_model().existing_objects.get(id=request.id)

        if user == context.caller:
            raise PermissionDenied("invalid_user")

        report_content.delay(
            content_type_id=ContentType.objects.get_for_model(get_user_model()).id,
            target_id=request.id,
            reporter_id=str(context.caller.id),
        )
        return empty_pb2.Empty()

    def Absolve(
        self, request: id_pb2.StringId, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        if not context.caller.is_staff:
            raise PermissionDenied("caller_not_staff")

        user = get_user_model().existing_objects.get(id=request.id)

        if user == context.caller:
            raise PermissionDenied("user_is_caller")

        delete_notifications_for(user)
        return empty_pb2.Empty()

    @atomic
    def Ban(
        self, request: user_pb2.BanSentence, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        if not context.caller.is_staff:
            raise PermissionDenied("caller_not_staff")

        user = get_user_model().existing_objects.select_for_update().get(id=request.id)

        if user == context.caller:
            raise PermissionDenied("invalid_user")
        elif user.is_superuser and not context.caller.is_superuser:
            raise PermissionDenied("caller_not_superuser")

        user.ban(timedelta(days=request.days) if request.days else None)
        return empty_pb2.Empty()

    @atomic
    def Promote(
        self, request: user_pb2.Promotion, context: grpc.ServicerContext
    ) -> empty_pb2.Empty:
        if not context.caller.is_superuser:
            raise PermissionDenied("caller_not_superuser")

        user = get_user_model().existing_objects.select_for_update().get(id=request.id)

        if request.rank == user_pb2.RANK_UNSPECIFIED:
            raise InvalidArgument("RANK_unspecified")
        elif request.rank == user_pb2.RANK_SUPERUSER and not user.is_staff:
            raise PermissionDenied("unsupported_promotion")

        user.is_staff = request.rank >= user_pb2.RANK_STAFF
        user.is_superuser = request.rank >= user_pb2.RANK_SUPERUSER
        user.save()
        return empty_pb2.Empty()
