from django.db.models import Q
from rest_framework.decorators import action
from rest_framework.mixins import (
    CreateModelMixin,
    DestroyModelMixin,
    ListModelMixin,
    UpdateModelMixin,
)
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.status import HTTP_200_OK

from core.pagination import LimitOffsetPagination
from core.permissions import CurrentUserIsOwnerOrReadOnly
from core.signals import fetched
from core.utils import str_to_bool
from core.viewsets import GenericViewSet, ModelViewSet

from .mixins import PostChildViewSetMixin
from .models import Chunk, Comment, Post, Stack
from .permissions import (
    CurrentUserCanComment,
    CurrentUserCanVote,
    CurrentUserIsDraftOwner,
    CurrentUserIsParentPostOwner,
    ParentPostIsDraft,
    ParentPostIsPublished,
    PostCanBePublished,
    PostIsPublished,
    PostPermission,
)
from .serializers import (
    ChunkSerializer,
    CommentSerializer,
    PostSerializer,
    VoteSerializer,
)
from .tasks import use_token


class PostViewSet(ModelViewSet):
    permission_classes = ModelViewSet.permission_classes + [
        PostPermission,
        CurrentUserIsDraftOwner,
    ]
    queryset = Post.objects.all()
    serializer_class = PostSerializer

    def list(self, request, *args, **kwargs) -> Response:
        drafts = not str_to_bool(request.query_params.get("published", "true"))
        filter = Q(author=request.user, date_published__isnull=drafts)
        return self._respond_with_page(filter=filter)

    @action(methods=["GET"], detail=False)
    def subscribed(self, request: Request) -> Response:
        filter = Q(subscribers=request.user, date_published__isnull=False)
        return self._respond_with_page(filter=filter)

    @action(methods=["GET"], detail=False)
    def feed(self, request: Request) -> Response:
        use_token.delay(token_key=request.auth.key)
        stack = Stack.objects.get(user=request.user)
        stack.fill()
        context = self.get_serializer_context()
        context["preview"] = False
        serializer = self.get_serializer(
            instance=stack.posts.all(), context=context, many=True
        )
        return Response(status=HTTP_200_OK, data=serializer.data)

    @action(
        methods=["POST"],
        detail=True,
        permission_classes=permission_classes + [PostCanBePublished],
    )
    def publish(self, request: Request, pk: str) -> Response:
        self.get_object().publish(anonymous=request.data.get("anonymous", False))
        return Response(status=HTTP_200_OK)

    def _respond_with_page(self, filter: Q) -> Response:
        queryset = self.filter_queryset(
            self.get_queryset().filter(filter, is_deleted=False)
        )
        page = self.paginate_queryset(queryset)
        context = self.get_serializer_context()
        serializer = self.get_serializer(instance=page, context=context, many=True)
        return self.get_paginated_response(serializer.data)


class PostInteractionViewSet(GenericViewSet):
    permission_classes = GenericViewSet.permission_classes + [PostIsPublished]
    queryset = Post.objects.all()
    serializer_class = PostSerializer

    @action(
        methods=["POST"],
        detail=True,
        permission_classes=GenericViewSet.permission_classes + [CurrentUserCanVote],
    )
    def vote(self, request: Request, pk: str) -> Response:
        context = self.get_serializer_context()
        serializer = VoteSerializer(data=request.data, context=context)
        serializer.is_valid(raise_exception=True)
        serializer.save(post_id=self.get_object().id)
        return Response(status=HTTP_200_OK)

    @action(methods=["POST", "PUT"], detail=True)
    def subscription(self, request: Request, pk: str) -> Response:
        post = self.get_object()
        post.subscribers.add(request.user)
        return Response(status=HTTP_200_OK)

    @subscription.mapping.delete
    def subscription_destroy(self, request: Request, pk: str) -> Response:
        post = self.get_object()
        post.subscribers.remove(request.user)
        return Response(status=HTTP_200_OK)


class ChunkViewSet(
    PostChildViewSetMixin,
    CreateModelMixin,
    UpdateModelMixin,
    DestroyModelMixin,
    GenericViewSet,
):
    permission_classes = GenericViewSet.permission_classes + [
        CurrentUserIsParentPostOwner,
        ParentPostIsDraft,
    ]
    queryset = Chunk.objects.all()
    serializer_class = ChunkSerializer
    pagination_class = None


class CommentViewSet(
    PostChildViewSetMixin,
    ListModelMixin,
    CreateModelMixin,
    DestroyModelMixin,
    GenericViewSet,
):
    permission_classes = GenericViewSet.permission_classes + [
        CurrentUserIsOwnerOrReadOnly,
        ParentPostIsPublished,
        CurrentUserCanComment,
    ]
    queryset = Comment.objects.all()
    serializer_class = CommentSerializer
    pagination_class = LimitOffsetPagination

    def list(self, request: Request, *args, **kwargs) -> Response:
        response = super().list(request, *args, **kwargs)
        pk_set = [str(comment["id"]) for comment in response.data["results"]]
        fetched.send(sender=self.queryset.model, user=request.user, pk_set=pk_set)
        return response


class CommentInteractionViewSet(GenericViewSet):
    queryset = Comment.objects.all()
    serializer_class = CommentSerializer
