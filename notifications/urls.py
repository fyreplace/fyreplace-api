from core import routers

from .views import *

router = routers.DefaultRouter()
router.register(__package__, NotificationViewSet)
urlpatterns = router.urls
app_name = __package__
