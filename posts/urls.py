from rest_framework_extensions import routers

from .views import *

router = routers.ExtendedDefaultRouter()
post_basename = router.get_default_basename(PostViewSet)
posts_route = router.register(__package__, PostInteractionViewSet)

for viewset in [CommentInteractionViewSet, CommentViewSet]:
    basename = router.get_default_basename(viewset)
    posts_route.register(
        r"comments",
        viewset,
        basename=f"{post_basename}-{basename}",
        parents_query_lookups=["post_id"],
    )

router.register(__package__, PostViewSet).register(
    r"chunks",
    ChunkViewSet,
    basename=f"{post_basename}-{router.get_default_basename(ChunkViewSet)}",
    parents_query_lookups=["post_id"],
)

urlpatterns = router.urls
app_name = __package__
