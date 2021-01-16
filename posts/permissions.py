from rest_framework.exceptions import ValidationError
from rest_framework.generics import get_object_or_404
from rest_framework.permissions import SAFE_METHODS, BasePermission
from rest_framework.request import Request

from .mixins import PostChildMixin
from .models import Post, Vote


class PostPermission(BasePermission):
    accepted_methods = [*SAFE_METHODS, "DELETE"]

    def has_object_permission(self, request: Request, view, obj) -> bool:
        return (
            request.method in self.accepted_methods
            or obj.date_published is None
            or obj.is_deleted
        )


class CurrentUserIsDraftOwner(BasePermission):
    def has_object_permission(self, request: Request, view, obj) -> bool:
        return request.user == obj.author or (
            request.method in SAFE_METHODS and obj.date_published is not None
        )


class PostIsDraft(BasePermission):
    def has_object_permission(self, request: Request, view, obj) -> bool:
        return obj.date_published is None


class PostIsPublished(PostIsDraft):
    def has_object_permission(self, request: Request, view, obj) -> bool:
        return not super().has_object_permission(request, view, obj)


class PostCanBePublished(PostIsDraft):
    def has_object_permission(self, request: Request, view, obj) -> bool:
        if super().has_object_permission(request, view, obj):
            try:
                obj.validate()
                return True
            except ValidationError:
                pass

        return False


class ParentPostIsDraft(PostChildMixin, BasePermission):
    def has_permission(self, request: Request, view) -> bool:
        post = get_object_or_404(Post, id=self.get_post_id(request))
        return post.date_published is None


class ParentPostIsPublished(ParentPostIsDraft):
    def has_permission(self, request: Request, view) -> bool:
        return not super().has_permission(request, view)


class CurrentUserIsParentPostOwner(PostChildMixin, BasePermission):
    def has_permission(self, request: Request, view) -> bool:
        post = get_object_or_404(Post, id=self.get_post_id(request))
        return request.user == post.author


class CurrentUserCanVote(BasePermission):
    def has_object_permission(self, request: Request, view, obj) -> bool:
        return (
            obj in request.user.stack.posts.all()
            and not Vote.objects.filter(user=request.user, post=obj).exists()
        )


class CurrentUserCanComment(PostChildMixin, BasePermission):
    accepted_methods = [*SAFE_METHODS, "DELETE"]

    def has_permission(self, request: Request, view):
        post = Post.objects.get(id=self.get_post_id(request))
        return (
            request.method in self.accepted_methods
            or post.is_anonymous
            or not post.author.blocked_users.filter(id=request.user.id).exists()
        )
