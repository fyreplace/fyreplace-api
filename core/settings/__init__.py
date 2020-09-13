import os
from pathlib import Path

import dj_database_url
from celery.schedules import crontab
from dotenv import find_dotenv, load_dotenv

from ..utils import str_to_bool
from . import compat

load_dotenv(find_dotenv())

BASE_DIR = Path(__file__).resolve().parent.parent.parent

SECRET_KEY = os.getenv("SECRET_KEY")

ALLOWED_HOSTS = []

APP_NAME = "fyreplace"

PRETTY_APP_NAME = APP_NAME.capitalize()

GRAVATAR_BASE_URL = "https://www.gravatar.com"

# Development

DEBUG = str_to_bool(os.getenv("DEBUG", "false"))

TEST_RUNNER = "core.tests.PytestTestRunner"

# Application definition

INSTALLED_APPS = [
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "django_celery_beat",
    "django_celery_results",
    "django_extensions",
    "rest_framework",
    "health_check",
    "health_check.db",
    "health_check.storage",
    "health_check.contrib.migrations",
    "health_check.contrib.celery",
    "health_check.contrib.celery_ping",
    "health_check.contrib.psutil",
    "health_check.contrib.rabbitmq",
    "core",
    "users.apps.UsersConfig",
]

MIDDLEWARE = [
    "django.middleware.gzip.GZipMiddleware",
    "django.middleware.security.SecurityMiddleware",
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

# Database

DATABASES = {"default": dj_database_url.config(conn_max_age=60 * 10)}

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

# Internationalization

LANGUAGE_CODE = "en-us"

TIME_ZONE = "UTC"

USE_I18N = True

USE_L10N = True

USE_TZ = True

# Static and media files

STATIC_URL = "/static/"

STATIC_ROOT = BASE_DIR / "static"

MEDIA_URL = "/media/"

MEDIA_ROOT = BASE_DIR / "media"

FILE_UPLOAD_MAX_MEMORY_SIZE = 1 * 1024 * 1024

DEFAULT_FILE_STORAGE = "core.storages.FileSystemStorage"

# Emails

if DEBUG:
    EMAIL_BACKEND = "django.core.mail.backends.console.EmailBackend"

DEFAULT_FROM_EMAIL = os.getenv("DEFAULT_FROM_EMAIL")

SERVER_EMAIL = os.getenv("SERVER_EMAIL")

ADMINS = [tuple(admin.split(":")) for admin in os.getenv("ADMINS", "").split(",")]

# Rest framework

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": [],
    "DEFAULT_PERMISSION_CLASSES": [
        "rest_framework.permissions.IsAuthenticatedOrReadOnly"
    ],
    "DEFAULT_THROTTLE_CLASSES": [
        "rest_framework.throttling.AnonRateThrottle",
        "rest_framework.throttling.UserRateThrottle",
    ],
    "DEFAULT_THROTTLE_RATES": {
        "anon": "3/second",
        "user": "10/second",
    },
}

# Celery

BROKER_URL = os.getenv("BROKER_URL")

CELERY_BEAT_SCHEDULER = "django_celery_beat.schedulers:DatabaseScheduler"

CELERY_BEAT_SCHEDULE = {
    "users.cleanup_users": {
        "task": "users.tasks.cleanup_users",
        "schedule": crontab(minute=0),
    },
    "users.cleanup_tokens": {
        "task": "users.tasks.cleanup_tokens",
        "schedule": crontab(hour=0, minute=0),
    },
}

CELERY_RESULT_BACKEND = "django-db"
