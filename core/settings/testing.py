from . import *

IS_TESTING = True

AWS_ACCESS_KEY_ID = None

STATICFILES_STORAGE = "django.contrib.staticfiles.storage.StaticFilesStorage"

DEFAULT_FILE_STORAGE = "core.storages.FileSystemStorage"

CELERY_TASK_ALWAYS_EAGER = True

CELERY_EAGER_PROPAGATES = True

APNS_PRIVATE_KEY = None

FIREBASE_APP = None

GRAVATAR_BASE_URL = None
