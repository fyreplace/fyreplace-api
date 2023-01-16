import json
import os
from base64 import b64decode
from datetime import timedelta
from pathlib import Path

import dj_database_url
import firebase_admin
import sentry_sdk
from celery.schedules import crontab
from dotenv import find_dotenv, load_dotenv
from firebase_admin.credentials import Certificate as FirebaseCertificate
from sentry_sdk.integrations.celery import CeleryIntegration
from sentry_sdk.integrations.django import DjangoIntegration

from ..utils import str_to_bool

# Development

load_dotenv(find_dotenv())

if debug_str := os.getenv("DEBUG", "False"):
    DEBUG = str_to_bool(debug_str)
else:
    DEBUG = False

TEST_RUNNER = "core.tests.PytestTestRunner"

IS_TESTING = False

if SENTRY_DSN := os.getenv("SENTRY_DSN"):
    sentry_sdk.init(
        dsn=SENTRY_DSN,
        environment=os.getenv("SENTRY_ENVIRONMENT"),
        integrations=[
            DjangoIntegration(),
            CeleryIntegration(),
        ],
        send_default_pii=True,
    )

# Self-awareness

BASE_DIR = Path(__file__).resolve().parent.parent.parent

SECRET_KEY = os.getenv("SECRET_KEY")

BASE_URL = os.getenv("BASE_URL")

ALLOWED_HOSTS = []

for host in os.getenv("ALLOWED_HOSTS", "").split(","):
    host = host.strip()

    if host:
        ALLOWED_HOSTS.append(host)

if len(ALLOWED_HOSTS) == 0:
    ALLOWED_HOSTS = ["*"]

APP_NAME = "fyreplace"

PRETTY_APP_NAME = APP_NAME.capitalize()

# Application definition

INSTALLED_APPS = [
    "daphne",
    "whitenoise.runserver_nostatic",
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "django_celery_beat",
    "django_extensions",
    "health_check",
    "health_check.db",
    "health_check.contrib.migrations",
    "rest_framework",
    "drf_spectacular",
    "anymail",
    "tooling",
    "core",
    "users",
    "posts",
    "notifications",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "whitenoise.middleware.WhiteNoiseMiddleware",
    "django.middleware.gzip.GZipMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "core.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

WSGI_APPLICATION = "core.wsgi.application"

ASGI_APPLICATION = "core.asgi.application"

# Database

DATABASES = {
    "default": dj_database_url.config(conn_max_age=600, conn_health_checks=True)
}

DEFAULT_AUTO_FIELD = "django.db.models.AutoField"

CONN_MAX_AGE = None

CONN_HEALTH_CHECKS = True

# Authentication

AUTH_USER_MODEL = "users.User"

AUTH_PASSWORD_VALIDATORS = [
    {"NAME": f"django.contrib.auth.password_validation.{validator}"}
    for validator in [
        "UserAttributeSimilarityValidator",
        "MinimumLengthValidator",
        "CommonPasswordValidator",
        "NumericPasswordValidator",
    ]
]

PASSWORD_HASHERS = [
    f"django.contrib.auth.hashers.{hasher}"
    for hasher in [
        "Argon2PasswordHasher",
        "PBKDF2PasswordHasher",
        "PBKDF2SHA1PasswordHasher",
        "BCryptSHA256PasswordHasher",
    ]
]

# Rest framework

REST_FRAMEWORK = {
    "EXCEPTION_HANDLER": "core.views.exception_handler",
    "DEFAULT_THROTTLE_CLASSES": [
        "rest_framework.throttling.AnonRateThrottle",
        "rest_framework.throttling.UserRateThrottle",
    ],
    "DEFAULT_THROTTLE_RATES": {
        "anon": "1/second",
        "user": "5/second",
    },
    "DEFAULT_PAGINATION_CLASS": "rest_framework.pagination.CursorPagination",
    "PAGE_SIZE": 12,
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
}

# JSON Web Tokens

JWT_ALGORITHM = "HS256"

# Internationalization

LANGUAGE_CODE = "en-us"

TIME_ZONE = "UTC"

USE_I18N = True

USE_TZ = True

# Static and media files

AWS_ACCESS_KEY_ID = os.getenv("S3_ACCESS_KEY_ID")

AWS_SECRET_ACCESS_KEY = os.getenv("S3_SECRET_ACCESS_KEY")

AWS_DEFAULT_ACL = "public-read"

AWS_QUERYSTRING_AUTH = False

AWS_STORAGE_BUCKET_NAME = os.getenv("S3_STORAGE_BUCKET_NAME")

AWS_S3_REGION_NAME = os.getenv("S3_REGION_NAME")

AWS_S3_ENDPOINT_URL = os.getenv("S3_ENDPOINT_URL")

AWS_S3_CUSTOM_DOMAIN = os.getenv("S3_CUSTOM_DOMAIN")

AWS_S3_ADDRESSING_STYLE = "virtual"

STATIC_URL = "/static/"

STATIC_ROOT = BASE_DIR / "static"

MEDIA_URL = "/media/"

MEDIA_ROOT = BASE_DIR / "media"

FILE_UPLOAD_MAX_MEMORY_SIZE = 1 * 1024 * 1024

STATICFILES_STORAGE = "whitenoise.storage.CompressedManifestStaticFilesStorage"

DEFAULT_FILE_STORAGE = (
    "core.storages.BotoStorage"
    if AWS_ACCESS_KEY_ID
    else "core.storages.FileSystemStorage"
)

VALID_IMAGE_MIMES = [f"image/{i}" for i in ("png", "jpeg", "webp")]

# Emails

ANYMAIL = {
    "MAILGUN_API_URL": os.getenv("MAILGUN_API_URL"),
    "MAILGUN_API_KEY": os.getenv("MAILGUN_API_KEY"),
    "MAILGUN_WEBHOOK_SIGNING_KEY": os.getenv("MAILGUN_WEBHOOK_SIGNING_KEY"),
}

if ANYMAIL["MAILGUN_API_KEY"]:
    EMAIL_BACKEND = "anymail.backends.mailgun.EmailBackend"
else:
    EMAIL_BACKEND = "django.core.mail.backends.console.EmailBackend"

DEFAULT_FROM_EMAIL = os.getenv("DEFAULT_FROM_EMAIL")

SERVER_EMAIL = os.getenv("SERVER_EMAIL")

ADMINS = [
    tuple([part.strip() for part in admin.split(":")])
    for admin in os.getenv("ADMINS", "").split(",")
    if admin
]

EMAIL_LINKS_DOMAIN = os.getenv("EMAIL_LINKS_DOMAIN")

# Celery

CELERY_BROKER_URL = os.getenv("CELERY_BROKER_URL")

CELERY_BROKER_TRANSPORT_OPTIONS = {
    "global_keyprefix": os.getenv("CELERY_BROKER_KEY_PREFIX")
}

CELERY_RESULT_BACKEND = os.getenv("CELERY_RESULT_BACKEND")

CELERY_IGNORE_RESULT = not CELERY_RESULT_BACKEND

CELERY_TASK_ROUTES = {
    "*.send_*": "messaging",
    "*.remove_*": "trash",
    "*.cleanup_*": "trash",
}

CELERY_BEAT_SCHEDULER = "django_celery_beat.schedulers:DatabaseScheduler"

CELERY_BEAT_SCHEDULE = {
    "users.cleanup_users": {
        "task": "users.tasks.cleanup_users",
        "schedule": crontab(minute=0),
    },
    "users.cleanup_connections": {
        "task": "users.tasks.cleanup_connections",
        "schedule": crontab(hour=0, minute=0),
    },
    "posts.cleanup_stacks": {
        "task": "posts.tasks.cleanup_stacks",
        "schedule": crontab(minute=0),
    },
    "notifications.refresh_apns_token": {
        "task": "notifications.tasks.refresh_apns_token",
        "schedule": crontab(minute="0,30"),
    },
}

# Apple Push Notification Serivce

APPLE_TEAM_ID = os.getenv("APPLE_TEAM_ID")

APPLE_APP_ID = os.getenv("APPLE_APP_ID")

APNS_URL = os.getenv("APNS_URL")

APNS_PRIVATE_KEY_ID = os.getenv("APNS_PRIVATE_KEY_ID")

if base64_data := os.getenv("APNS_PRIVATE_KEY_B64"):
    APNS_PRIVATE_KEY = b64decode(base64_data)
elif path := os.getenv("APNS_PRIVATE_KEY_PATH"):
    with open(path, "rb") as file:
        APNS_PRIVATE_KEY = file.read()
else:
    APNS_PRIVATE_KEY = None

# Firebase


try:
    FIREBASE_APP = firebase_admin.get_app()
except ValueError:
    if base64_data := os.getenv("FIREBASE_ACCOUNT_B64"):
        data = json.loads(b64decode(base64_data))
        certificate = FirebaseCertificate(data)
    elif path := os.getenv("FIREBASE_ACCOUNT_PATH"):
        certificate = FirebaseCertificate(path)
    else:
        certificate = None

    FIREBASE_APP = firebase_admin.initialize_app(certificate)
else:
    FIREBASE_APP = None

# Fyreplace

FYREPLACE_INACTIVE_USER_DURATION = timedelta(days=1)

FYREPLACE_CONNECTION_DURATION = timedelta(weeks=4)

FYREPLACE_POST_SPREAD_LIFE = 3

FYREPLACE_POST_MAX_DURATION = timedelta(weeks=1)

# Other

PAGINATION_MAX_SIZE = 50

GRAVATAR_BASE_URL = "https://www.gravatar.com"
