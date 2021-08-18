from datetime import datetime, timedelta
from uuid import uuid4

from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractUser
from django.contrib.contenttypes.models import ContentType
from django.core.exceptions import ObjectDoesNotExist, ValidationError
from django.core.files.images import ImageFile
from django.db.utils import DataError, IntegrityError
from django.utils.timezone import get_current_timezone, now
from grpc_interceptor.exceptions import (
    AlreadyExists,
    InvalidArgument,
    PermissionDenied,
    Unauthenticated,
)

from core import jwt
from core.storages import get_image_url
from core.tests import ImageTestCaseMixin, get_asset
from notifications.models import CountUnit, Notification
from notifications.tests import BaseNotificationTestCase
from protos import id_pb2, user_pb2

from .emails import AccountActivationEmail, AccountRecoveryEmail, UserEmailUpdateEmail
from .models import Connection
from .services import AccountService, UserService
from .tests import AuthenticatedTestCase, BaseUserTestCase, make_email


class AccountServiceTestCase(BaseUserTestCase):
    def setUp(self):
        super().setUp()
        self.service = AccountService()

    def _make_create_request(self, **kwargs):
        return user_pb2.UserCreation(
            email=make_email("new"),
            username="new",
            password=self.STRONG_PASSWORD,
        )


class UserServiceTestCase(AuthenticatedTestCase, BaseNotificationTestCase):
    def setUp(self):
        super().setUp()
        self.service = UserService()


class AccountService_Create(AccountServiceTestCase):
    def setUp(self):
        super().setUp()
        self.user_count = get_user_model().objects.count()
        self.request = self._make_create_request()

    def test(self):
        self.service.Create(self.request, self.grpc_context)
        user: AbstractUser = get_user_model().objects.latest("date_joined")
        self.assertEqual(get_user_model().objects.count(), self.user_count + 1)
        self.assertEmails([AccountActivationEmail(user.id)])
        self.assertFalse(user.is_active)
        self.assertFalse(user.is_staff)
        self.assertFalse(user.is_superuser)

    def test_username_too_short(self):
        self.request.username = "a"

        with self.assertRaises(ValidationError):
            self.service.Create(self.request, self.grpc_context)

        self.assertEqual(get_user_model().objects.count(), self.user_count)

    def test_username_too_long(self):
        self.request.username = "a" * (get_user_model().username.field.max_length + 1)

        with self.assertRaises((ValidationError, DataError)):
            self.service.Create(self.request, self.grpc_context)

        self.assertEqual(get_user_model().objects.count(), self.user_count)

    def test_username_already_taken(self):
        self.request.username = self.main_user.username

        with self.assertRaises(AlreadyExists):
            self.service.Create(self.request, self.grpc_context)

        self.assertEqual(get_user_model().objects.count(), self.user_count)

    def test_username_reserved(self):
        self.request.username = "admin"

        with self.assertRaises(PermissionDenied):
            self.service.Create(self.request, self.grpc_context)

        self.assertEqual(get_user_model().objects.count(), self.user_count)

    def test_bad_username(self):
        self.request.username = "bad username"

        with self.assertRaises(InvalidArgument):
            self.service.Create(self.request, self.grpc_context)

        self.assertEqual(get_user_model().objects.count(), self.user_count)

    def test_bad_email(self):
        self.request.email = "bad"

        with self.assertRaises(InvalidArgument):
            self.service.Create(self.request, self.grpc_context)

        self.assertEqual(get_user_model().objects.count(), self.user_count)

    def test_email_already_taken(self):
        self.request.email = self.main_user.email

        with self.assertRaises(AlreadyExists):
            self.service.Create(self.request, self.grpc_context)

        self.assertEqual(get_user_model().objects.count(), self.user_count)

    def test_bad_password(self):
        self.request.password = "password"

        with self.assertRaises(InvalidArgument):
            self.service.Create(self.request, self.grpc_context)

        self.assertEqual(get_user_model().objects.count(), self.user_count)


class AccountService_Delete(AccountServiceTestCase, AuthenticatedTestCase):
    def test(self):
        self.service.Delete(self.request, self.grpc_context)
        self.main_user.refresh_from_db()
        self.assertIsNone(self.main_user.username)
        self.assertIsNone(self.main_user.email)
        self.assertFalse(self.main_user.has_usable_password())
        self.assertFalse(self.main_user.is_active)
        self.assertTrue(self.main_user.is_deleted)
        self.assertFalse(self.main_user.avatar)
        self.assertEqual(self.main_user.bio, "")
        self.assertEqual(self.main_user.blocked_users.count(), 0)


class AccountService_SendActivationEmail(AccountServiceTestCase):
    def setUp(self):
        super().setUp()
        self.request = user_pb2.Email(email=make_email("new"))

    def test(self):
        request = self._make_create_request()
        self.service.Create(request, self.grpc_context)
        user = get_user_model().objects.get(email=request.email)
        email = AccountActivationEmail(user.id)
        self.assertEmails([email])
        self.service.SendActivationEmail(self.request, self.grpc_context)
        self.assertEmails([email, email])

    def test_active_user(self):
        request = self._make_create_request()
        self.service.Create(request, self.grpc_context)
        user = get_user_model().objects.get(email=request.email)
        email = AccountActivationEmail(user.id)
        self.assertEmails([email])
        user.is_active = True
        user.save()
        self.service.SendActivationEmail(self.request, self.grpc_context)
        self.assertEmails([email])

    def test_non_existent_user(self):
        self.service.SendActivationEmail(self.request, self.grpc_context)
        self.assertEmails([])


class AccountService_ConfirmActivation(AccountServiceTestCase):
    def setUp(self):
        super().setUp()
        self.service.Create(self._make_create_request(), self.grpc_context)
        self.user: AbstractUser = get_user_model().objects.latest("date_joined")
        self.connection_count = Connection.objects.count()
        self.connection_client = user_pb2.Client(
            hardware=Connection.Hardware.UNKNOWN,
            software=Connection.Software.UNKNOWN,
        )
        self.request = user_pb2.ConnectionToken(
            token=AccountActivationEmail(self.user.id).token,
            client=self.connection_client,
        )

    def test(self):
        token = self.service.ConfirmActivation(self.request, self.grpc_context)
        self.assertEqual(Connection.objects.count(), self.connection_count + 1)
        connection = Connection.objects.latest("date_created")
        claims = jwt.decode(token.token)
        self.assertIn("user_id", claims)
        self.assertIn("connection_id", claims)
        self.assertEqual(claims["user_id"], str(self.user.id))
        self.assertEqual(claims["connection_id"], connection.id)
        self.user.refresh_from_db()
        self.assertTrue(self.user.is_active)
        self.assertEqual(connection.user, self.user)
        self.assertEqual(connection.hardware, Connection.Hardware.UNKNOWN)
        self.assertEqual(connection.software, Connection.Software.UNKNOWN)

    def test_missing_user_id(self):
        token = jwt.encode({"timestamp": now().timestamp()})
        self.request = user_pb2.ConnectionToken(
            token=token, client=self.connection_client
        )

        with self.assertRaises(Unauthenticated):
            self.service.ConfirmActivation(self.request, self.grpc_context)

        self.user.refresh_from_db()
        self.assertFalse(self.user.is_active)
        self.assertEqual(Connection.objects.count(), self.connection_count)

    def test_expired_timestamp(self):
        token = jwt.encode(
            {
                "user_id": str(self.user.id),
                "timestamp": (now() + timedelta(hours=-1)).timestamp(),
            }
        )
        self.request = user_pb2.ConnectionToken(
            token=token, client=self.connection_client
        )

        with self.assertRaises(Unauthenticated):
            self.service.ConfirmActivation(self.request, self.grpc_context)

        self.user.refresh_from_db()
        self.assertFalse(self.user.is_active)
        self.assertEqual(Connection.objects.count(), self.connection_count)

    def test_already_active_user(self):
        self.user.is_active = True
        self.user.save()

        with self.assertRaises(PermissionDenied):
            self.service.ConfirmActivation(self.request, self.grpc_context)

        self.assertEqual(Connection.objects.count(), self.connection_count)

    def test_deleted_user(self):
        self.user.delete()

        with self.assertRaises(PermissionDenied):
            self.service.ConfirmActivation(self.request, self.grpc_context)

        self.assertEqual(Connection.objects.count(), self.connection_count)

    def test_banned_user(self):
        self.user.ban(timedelta(days=3))

        with self.assertRaises(PermissionDenied):
            self.service.ConfirmActivation(self.request, self.grpc_context)

        self.assertEqual(Connection.objects.count(), self.connection_count)


class AccountService_SendRecoveryEmail(AccountServiceTestCase):
    def setUp(self):
        super().setUp()
        self.request = user_pb2.Email(email=self.main_user.email)

    def test(self):
        self.service.SendRecoveryEmail(self.request, self.grpc_context)
        self.assertEmails([AccountRecoveryEmail(self.main_user.id)])

    def test_unused_email(self):
        self.request.email = make_email("bad")
        self.service.SendRecoveryEmail(self.request, self.grpc_context)
        self.assertEmails([])

    def test_bad_email(self):
        self.request.email = "not an email"

        with self.assertRaises(InvalidArgument):
            self.service.SendRecoveryEmail(self.request, self.grpc_context)


class AccountService_ConfirmRecovery(AccountServiceTestCase, AuthenticatedTestCase):
    def setUp(self):
        super().setUp()
        self.connection_count = Connection.objects.count()
        self.request = user_pb2.ConnectionToken(
            token=AccountActivationEmail(self.main_user.id).token,
            client=self.main_connection.to_message().client,
        )

    def test(self):
        token = self.service.ConfirmRecovery(self.request, self.grpc_context)
        self.assertEqual(Connection.objects.count(), self.connection_count + 1)
        connection = Connection.objects.latest("date_created")
        claims = jwt.decode(token.token)
        self.assertIn("user_id", claims)
        self.assertIn("connection_id", claims)
        self.assertEqual(claims["user_id"], str(self.main_user.id))
        self.assertEqual(claims["connection_id"], connection.id)
        self.main_user.refresh_from_db()
        self.assertTrue(self.main_user.is_active)
        self.assertEqual(connection.user, self.main_user)
        self.assertEqual(connection.hardware, Connection.Hardware.UNKNOWN)
        self.assertEqual(connection.software, Connection.Software.UNKNOWN)

    def test_pending_user(self):
        self.main_user.is_active = False
        self.main_user.save()

        with self.assertRaises(PermissionDenied):
            self.service.ConfirmRecovery(self.request, self.grpc_context)

        self.assertEqual(Connection.objects.count(), self.connection_count)

    def test_deleted_user(self):
        self.main_user.delete()

        with self.assertRaises(PermissionDenied):
            self.service.ConfirmRecovery(self.request, self.grpc_context)

        self.assertEqual(Connection.objects.count(), self.connection_count - 1)

    def test_banned_user(self):
        self.main_user.ban(timedelta(days=3))

        with self.assertRaises(PermissionDenied):
            self.service.ConfirmRecovery(self.request, self.grpc_context)

        self.assertEqual(Connection.objects.count(), self.connection_count - 1)


class AccountService_ListConnections(AccountServiceTestCase, AuthenticatedTestCase):
    def setUp(self):
        super().setUp()
        Connection.objects.create(user=self.other_user)
        Connection.objects.create(user=self.main_user)

    def test(self):
        connections = self.service.ListConnections(self.request, self.grpc_context)
        main_user_connections = Connection.objects.filter(user=self.main_user)
        ids = [c.id for c in connections.connections]
        self.assertEqual(len(ids), len(main_user_connections))

        for connection in Connection.objects.filter(user=self.main_user):
            self.assertIn(connection.id, ids)

        for connection in Connection.objects.filter(user=self.other_user):
            self.assertNotIn(connection.id, ids)


class AccountService_Connect(AccountServiceTestCase):
    def setUp(self):
        super().setUp()
        self.request = user_pb2.Credentials(
            identifier=self.main_user.username,
            password=self.MAIN_USER_PASSWORD,
            client=user_pb2.Client(
                hardware=Connection.Hardware.UNKNOWN,
                software=Connection.Software.UNKNOWN,
            ),
        )

    def test(self):
        connection_count = Connection.objects.count()
        token = self.service.Connect(self.request, self.grpc_context)
        self.assertEqual(Connection.objects.count(), connection_count + 1)
        connection = Connection.objects.latest("date_created")
        claims = jwt.decode(token.token)
        self.assertIn("user_id", claims)
        self.assertIn("connection_id", claims)
        self.assertEqual(claims["user_id"], str(self.main_user.id))
        self.assertEqual(claims["connection_id"], connection.id)

    def test_bad_username(self):
        self.request.identifier = "bad"

        with self.assertRaises(InvalidArgument):
            self.service.Connect(self.request, self.grpc_context)

    def test_bad_password(self):
        self.request.password = "bad"

        with self.assertRaises(InvalidArgument):
            self.service.Connect(self.request, self.grpc_context)

    def test_pending(self):
        self.main_user.is_active = False
        self.main_user.save()

        with self.assertRaises(PermissionDenied):
            self.service.Connect(self.request, self.grpc_context)

    def test_deleted(self):
        self.main_user.delete()

        with self.assertRaises(InvalidArgument):
            self.service.Connect(self.request, self.grpc_context)

    def test_banned(self):
        self.main_user.ban(timedelta(days=3))

        with self.assertRaises(PermissionDenied):
            self.service.Connect(self.request, self.grpc_context)


class AccountService_Disconnect(AccountServiceTestCase, AuthenticatedTestCase):
    def setUp(self):
        super().setUp()
        self.request = id_pb2.IntId()

    def test(self):
        connection_count = Connection.objects.count()
        self.service.Disconnect(self.request, self.grpc_context)
        self.assertEqual(Connection.objects.count(), connection_count - 1)
        self.assertNotIn(self.main_connection.id, Connection.objects.values("id"))

    def test_specific(self):
        connection = Connection.objects.create(user=self.main_user)
        connection_count = Connection.objects.count()
        self.request.id = connection.id
        self.service.Disconnect(self.request, self.grpc_context)
        self.assertEqual(Connection.objects.count(), connection_count - 1)

    def test_other(self):
        connection = Connection.objects.create(user=self.other_user)
        connection_count = Connection.objects.count()
        self.request.id = connection.id

        with self.assertRaises(ObjectDoesNotExist):
            self.service.Disconnect(self.request, self.grpc_context)

        self.assertEqual(Connection.objects.count(), connection_count)


class AccountService_DisconnectAll(AccountServiceTestCase, AuthenticatedTestCase):
    def test(self):
        Connection.objects.create(user=self.main_user)
        Connection.objects.create(user=self.other_user)
        connection_count = Connection.objects.count()
        self.service.DisconnectAll(self.request, self.grpc_context)
        self.assertEqual(Connection.objects.count(), connection_count - 2)
        self.assertEqual(Connection.objects.filter(user=self.main_user).count(), 0)


class UserService_Retrieve(UserServiceTestCase):
    def setUp(self):
        super().setUp()
        asset = open(get_asset("image.png"), "rb")
        self.other_user.bio = "Bio"
        self.other_user.avatar = ImageFile(file=asset, name="image")
        self.other_user.save()
        self.request = id_pb2.StringId(id=str(self.other_user.id))

    def test(self):
        user = self.service.Retrieve(self.request, self.grpc_context)
        self.assertEqual(user.id, str(self.other_user.id))
        self.assertAlmostEqual(
            datetime.fromtimestamp(user.date_joined.seconds, tz=get_current_timezone()),
            self.other_user.date_joined,
            delta=timedelta(seconds=1),
        )
        self.assertEqual(user.rank, user_pb2.RANK_CITIZEN)
        self.assertEqual(user.username, str(self.other_user.username))
        self.assertEqual(user.avatar.url, get_image_url(self.other_user.avatar))
        self.assertEqual(user.bio, self.other_user.bio)
        self.assertEqual(user.email, "")

    def test_banned_forever(self):
        self.other_user.ban()
        user = self.service.Retrieve(self.request, self.grpc_context)
        self.assertEqual(user.id, str(self.other_user.id))
        self.assertAlmostEqual(
            datetime.fromtimestamp(user.date_joined.seconds, tz=get_current_timezone()),
            self.other_user.date_joined,
            delta=timedelta(seconds=1),
        )
        self.assertEqual(user.rank, user_pb2.RANK_UNSPECIFIED)
        self.assertTrue(user.is_banned)
        self.assertEqual(user.username, "")
        self.assertEqual(user.avatar.url, "")
        self.assertEqual(user.bio, "")
        self.assertEqual(user.email, "")

    def test_deleted(self):
        self.other_user.delete()

        with self.assertRaises(ObjectDoesNotExist):
            self.service.Retrieve(self.request, self.grpc_context)

    def test_non_existent(self):
        self.request.id = str(uuid4())

        with (self.assertRaises(ObjectDoesNotExist)):
            self.service.Retrieve(self.request, self.grpc_context)


class UserService_RetrieveMe(UserServiceTestCase):
    def test(self):
        user = self.service.RetrieveMe(self.request, self.grpc_context)
        self.assertEqual(user.id, str(self.main_user.id))
        self.assertEqual(user.username, str(self.main_user.username))
        self.assertEqual(user.email, str(self.main_user.email))


class UserService_UpdateBio(UserServiceTestCase):
    def setUp(self):
        super().setUp()
        self.request = user_pb2.Bio(bio="Hello")

    def test(self):
        self.service.UpdateBio(self.request, self.grpc_context)
        self.main_user.refresh_from_db()
        self.assertEqual(self.main_user.bio, self.request.bio)

    def test_empty(self):
        self.main_user.bio = "Bio"
        self.main_user.save()
        self.request.bio = ""
        self.service.UpdateBio(self.request, self.grpc_context)
        self.main_user.refresh_from_db()
        self.assertEqual(self.main_user.bio, self.request.bio)

    def test_too_long(self):
        self.request.bio = "a" * (get_user_model().bio.field.max_length + 1)

        with self.assertRaises(ValidationError):
            self.service.UpdateBio(self.request, self.grpc_context)


class UserService_UpdateAvatar(ImageTestCaseMixin, UserServiceTestCase):
    def test(self):
        for extension in ("jpeg", "png"):
            self.service.UpdateAvatar(self.make_request(extension), self.grpc_context)
            self.main_user.refresh_from_db()
            self.assertRegex(str(self.main_user.avatar), r".*\." + extension)

    def test_empty(self):
        self.service.UpdateAvatar(self.make_request("jpeg"), self.grpc_context)
        self.service.UpdateAvatar([], self.grpc_context)
        self.main_user.refresh_from_db()
        self.assertEqual(str(self.main_user.avatar), "")

    def test_bad_format(self):
        with self.assertRaises(InvalidArgument):
            self.service.UpdateAvatar(self.make_request("txt"), self.grpc_context)


class UserService_UpdatePassword(UserServiceTestCase):
    def setUp(self):
        super().setUp()
        self.request = user_pb2.Password(password="New password")

    def test(self):
        self.service.UpdatePassword(self.request, self.grpc_context)
        self.main_user.refresh_from_db()
        self.assertTrue(self.main_user.check_password(self.request.password))

    def test_invalid_password(self):
        self.request.password = "weak"

        with self.assertRaises(ValidationError):
            self.service.UpdatePassword(self.request, self.grpc_context)


class UserService_SendEmailUpdateEmail(UserServiceTestCase):
    def setUp(self):
        super().setUp()
        self.request = user_pb2.Email(email=make_email("new"))

    def test(self):
        self.service.SendEmailUpdateEmail(self.request, self.grpc_context)
        self.assertEmails(
            [UserEmailUpdateEmail(self.main_user.id, self.main_user.email)]
        )

    def test_invalid_email(self):
        self.request.email = "bad"

        with self.assertRaises(ValidationError):
            self.service.SendEmailUpdateEmail(self.request, self.grpc_context)


class UserService_ConfirmEmailUpdate(UserServiceTestCase):
    def setUp(self):
        super().setUp()
        self.request = user_pb2.Token(token=self._make_token(self.main_user))

    def test(self):
        self.service.ConfirmEmailUpdate(self.request, self.grpc_context)
        self.main_user.refresh_from_db()
        self.assertEqual(self.main_user.email, make_email("new"))

    def test_other(self):
        self.request.token = self._make_token(self.other_user)

        with self.assertRaises(PermissionDenied):
            self.service.ConfirmEmailUpdate(self.request, self.grpc_context)

    def _make_token(self, user: get_user_model()):
        return UserEmailUpdateEmail(user.id, make_email("new")).token


class UserService_ListBlocked(UserServiceTestCase):
    def setUp(self):
        super().setUp()
        self.main_user.blocked_users.add(self.other_user)

    def test(self):
        profiles = self.service.ListBlocked(self.request, self.grpc_context)
        self.assertEqual(
            [p.id for p in profiles.profiles],
            [str(u.id) for u in self.main_user.blocked_users.all()],
        )


class UserService_UpdateBlock(UserServiceTestCase):
    def setUp(self):
        super().setUp()
        self.request = user_pb2.Block(id=str(self.other_user.id), blocked=True)

    def test(self):
        self.service.UpdateBlock(self.request, self.grpc_context)
        self.assertEqual(
            [u.id for u in self.main_user.blocked_users.all()],
            [self.other_user.id],
        )

    def test_block_me(self):
        self.request.id = str(self.main_user.id)

        with self.assertRaises(IntegrityError):
            self.service.UpdateBlock(self.request, self.grpc_context)

    def test_block_already_blocked(self):
        self.main_user.blocked_users.add(self.other_user)
        self.service.UpdateBlock(self.request, self.grpc_context)
        self.assertEqual(
            [u.id for u in self.main_user.blocked_users.all()],
            [self.other_user.id],
        )

    def test_unblock(self):
        self.request.blocked = False
        self.service.UpdateBlock(self.request, self.grpc_context)
        self.assertEqual(self.main_user.blocked_users.count(), 0)

    def test_unblock_not_blocked(self):
        self.request.blocked = False
        self.main_user.blocked_users.remove(self.other_user)
        self.service.UpdateBlock(self.request, self.grpc_context)
        self.assertEqual(self.main_user.blocked_users.count(), 0)

    def test_block_non_existent(self):
        self.request.id = str(uuid4())

        with self.assertRaises(ObjectDoesNotExist):
            self.service.UpdateBlock(self.request, self.grpc_context)


class UserService_Report(UserServiceTestCase):
    def setUp(self):
        super().setUp()
        self.request = id_pb2.StringId(id=str(self.other_user.id))

    def test(self):
        self.service.Report(self.request, self.grpc_context)
        self.assertEqual(Notification.flag_objects.count(), 1)
        flag = Notification.flag_objects.first()
        self.assertEqual(
            flag.target_type, ContentType.objects.get_for_model(get_user_model())
        )
        self.assertEqual(flag.target_id, str(self.other_user.id))

    def test_deleted(self):
        self.other_user.delete()

        with self.assertRaises(ObjectDoesNotExist):
            self.service.Report(self.request, self.grpc_context)

    def test_self(self):
        self.request.id = str(self.main_user.id)

        with self.assertRaises(PermissionDenied):
            self.service.Report(self.request, self.grpc_context)


class UserService_Absolve(UserServiceTestCase):
    def setUp(self):
        super().setUp()
        flag = Notification.flag_objects.create(target=self.other_user)
        CountUnit.objects.create(notification=flag, count_item=self.main_user)
        self.main_user.is_staff = True
        self.main_user.save()
        self.request = id_pb2.StringId(id=str(self.other_user.id))

    def test(self):
        self.service.Absolve(self.request, self.grpc_context)
        self.assertNoFlags(get_user_model(), self.other_user.id)

    def test_empty(self):
        Notification.flag_objects.all().delete()
        self.service.Absolve(self.request, self.grpc_context)
        self.assertNoFlags(get_user_model(), self.other_user.id)

    def test_not_staff(self):
        self.main_user.is_staff = False
        self.main_user.save()

        with self.assertRaises(PermissionDenied):
            self.service.Absolve(self.request, self.grpc_context)

    def test_on_self(self):
        self.request.id = str(self.main_user.id)

        with self.assertRaises(PermissionDenied):
            self.service.Absolve(self.request, self.grpc_context)


class UserService_Ban(UserServiceTestCase):
    def setUp(self):
        super().setUp()
        self.main_user.is_staff = True
        self.main_user.save()
        self.before = now()
        self.request = user_pb2.BanSentence(id=str(self.other_user.id), days=3)

    def test(self):
        self.service.Ban(self.request, self.grpc_context)
        self.other_user.refresh_from_db()
        self.assertTrue(self.other_user.is_banned)
        self.assertAlmostEqual(
            self.other_user.date_ban_end,
            self.before + timedelta(days=self.request.days),
            delta=timedelta(seconds=1),
        )

    def test_no_duration(self):
        self.request.ClearField("days")
        self.service.Ban(self.request, self.grpc_context)
        self.other_user.refresh_from_db()
        self.assertTrue(self.other_user.is_banned)
        self.assertIsNone(self.other_user.date_ban_end)

    def test_other_already_banned(self):
        self.other_user.ban()
        self.service.Ban(self.request, self.grpc_context)
        self.other_user.refresh_from_db()
        self.assertTrue(self.other_user.is_banned)
        self.assertAlmostEqual(
            self.other_user.date_ban_end,
            self.before + timedelta(days=self.request.days),
            delta=timedelta(seconds=1),
        )

    def test_other_staff(self):
        self.other_user.is_staff = True
        self.other_user.save()
        self.service.Ban(self.request, self.grpc_context)
        self.other_user.refresh_from_db()
        self.assertTrue(self.other_user.is_banned)
        self.assertAlmostEqual(
            self.other_user.date_ban_end,
            self.before + timedelta(days=self.request.days),
            delta=timedelta(seconds=1),
        )

    def test_not_staff(self):
        self.main_user.is_staff = False
        self.main_user.save()

        with self.assertRaises(PermissionDenied):
            self.service.Ban(self.request, self.grpc_context)

    def test_other_deleted(self):
        self.other_user.delete()

        with self.assertRaises(ObjectDoesNotExist):
            self.service.Ban(self.request, self.grpc_context)

    def test_other_superuser(self):
        self.other_user.is_superuser = True
        self.other_user.save()

        with self.assertRaises(PermissionDenied):
            self.service.Ban(self.request, self.grpc_context)


class UserService_Promote(UserServiceTestCase):
    def setUp(self):
        super().setUp()
        self.main_user.is_staff = True
        self.main_user.is_superuser = True
        self.main_user.save()
        self.request = user_pb2.Promotion(id=str(self.other_user.id))

    def test_citizen_to_staff(self):
        self.other_user.is_staff = False
        self.other_user.is_superuser = False
        self.other_user.save()
        self.request.rank = user_pb2.RANK_STAFF
        self.service.Promote(self.request, self.grpc_context)
        self.other_user.refresh_from_db()
        self.assertTrue(self.other_user.is_staff)
        self.assertFalse(self.other_user.is_superuser)

    def test_staff_to_superuser(self):
        self.other_user.is_staff = True
        self.other_user.is_superuser = False
        self.other_user.save()
        self.request.rank = user_pb2.RANK_SUPERUSER
        self.service.Promote(self.request, self.grpc_context)
        self.other_user.refresh_from_db()
        self.assertTrue(self.other_user.is_staff)
        self.assertTrue(self.other_user.is_superuser)

    def test_staff_to_citizen(self):
        self.other_user.is_staff = True
        self.other_user.is_superuser = False
        self.other_user.save()
        self.request.rank = user_pb2.RANK_CITIZEN
        self.service.Promote(self.request, self.grpc_context)
        self.other_user.refresh_from_db()
        self.assertFalse(self.other_user.is_staff)
        self.assertFalse(self.other_user.is_superuser)

    def test_superuser_to_staff(self):
        self.other_user.is_staff = True
        self.other_user.is_superuser = True
        self.other_user.save()
        self.request.rank = user_pb2.RANK_STAFF
        self.service.Promote(self.request, self.grpc_context)
        self.other_user.refresh_from_db()
        self.assertTrue(self.other_user.is_staff)
        self.assertFalse(self.other_user.is_superuser)

    def test_superuser_to_citizen(self):
        self.other_user.is_staff = True
        self.other_user.is_superuser = True
        self.other_user.save()
        self.request.rank = user_pb2.RANK_CITIZEN
        self.service.Promote(self.request, self.grpc_context)
        self.other_user.refresh_from_db()
        self.assertFalse(self.other_user.is_staff)
        self.assertFalse(self.other_user.is_superuser)

    def test_citizen_to_superuser(self):
        self.other_user.is_staff = False
        self.other_user.is_superuser = False
        self.other_user.save()
        self.request.rank = user_pb2.RANK_SUPERUSER

        with self.assertRaises(PermissionDenied):
            self.service.Promote(self.request, self.grpc_context)

    def test_to_unspecified(self):
        self.request.rank = user_pb2.RANK_UNSPECIFIED

        with self.assertRaises(InvalidArgument):
            self.service.Promote(self.request, self.grpc_context)

    def test_not_superuser(self):
        self.main_user.is_superuser = False
        self.main_user.save()
        self.request.rank = user_pb2.RANK_STAFF

        with self.assertRaises(PermissionDenied):
            self.service.Promote(self.request, self.grpc_context)
