from core import routers

from .views import *

router = routers.DefaultRouter()

for viewset in (UserInteractionViewSet, AccountViewSet, SetupViewSet, UserViewSet):
    router.register(__package__, viewset)

for viewset in (LoginViewSet, LogoutViewSet):
    router.register(r"tokens", viewset)

urlpatterns = router.urls
app_name = __package__
