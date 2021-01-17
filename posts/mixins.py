from django.db.models import QuerySet
from rest_framework.generics import get_object_or_404
from rest_framework.request import Request
from rest_framework.serializers import Serializer
from rest_framework_extensions.mixins import NestedViewSetMixin

from core.exceptions import Gone

from .models import Post


class PostChildMixin:
    def get_post_id(self, request: Request) -> str:
        context_kwargs = request.parser_context["kwargs"]
        post_id = context_kwargs.get("parent_lookup_post_id")

        if get_object_or_404(Post, id=post_id).is_deleted:
            raise Gone

        return post_id


class PostChildViewSetMixin(PostChildMixin, NestedViewSetMixin):
    def filter_queryset_by_parents_lookups(self, queryset: QuerySet) -> QuerySet:
        result = super().filter_queryset_by_parents_lookups(queryset)
        self.get_post_id(self.request)
        return result

    def perform_create(self, serializer: Serializer):
        serializer.save(post_id=self.get_post_id(self.request))

    def perform_update(self, serializer: Serializer):
        serializer.save(post_id=self.get_post_id(self.request))
