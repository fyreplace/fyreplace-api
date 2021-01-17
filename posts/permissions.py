from rest_framework.exceptions import ValidationError
from rest_framework.generics import get_object_or_404
from rest_framework.permissions import SAFE_METHODS, BasePermission
from rest_framework.request import Request

from core.permissions import CurrentUserIsOwnerOrReadOnly, reason

from .mixins import PostChildMixin
from .models import Post, Vote


class CurrentUserIsDraftOwner(CurrentUserIsOwnerOrReadOnly):
    def has_object_permission(self, request: Request, view, obj) -> bool:
        return request.user == obj.author or (
            request.method in SAFE_METHODS and obj.date_published is not None
        )


class PostIsDraft(BasePermission):
    message = reason("object_is_not_draft")

    def has_object_permission(self, request: Request, view, obj) -> bool:
        return obj.date_published is None


class PostIsDraftOrReadOnly(PostIsDraft):
    accepted_methods = [*SAFE_METHODS, "DELETE"]

    def has_object_permission(self, request: Request, view, obj) -> bool:
        return (
            super().has_object_permission(request, view, obj)
            or request.method in self.accepted_methods
        )


class PostIsPublished(PostIsDraft):
    message = reason("object_is_not_published")

    def has_object_permission(self, request: Request, view, obj) -> bool:
        return not super().has_object_permission(request, view, obj)


class PostIsValid(BasePermission):
    message = reason("object_is_not_valid")

    def has_object_permission(self, request: Request, view, obj) -> bool:
        if super().has_object_permission(request, view, obj):
            try:
                obj.validate()
            except ValidationError:
                pass
            else:
                return True

        return False


class ParentPostIsDraft(PostChildMixin, BasePermission):
    message = reason("parent_is_not_draft")

    def has_permission(self, request: Request, view) -> bool:
        post = get_object_or_404(Post, id=self.get_post_id(request))
        return post.date_published is None


class ParentPostIsPublished(ParentPostIsDraft):
    message = reason("parent_is_not_published")

    def has_permission(self, request: Request, view) -> bool:
        return not super().has_permission(request, view)


class CurrentUserIsParentPostOwner(PostChildMixin, BasePermission):
    message = reason("user_is_not_parent_owner")

    def has_permission(self, request: Request, view) -> bool:
        post = get_object_or_404(Post, id=self.get_post_id(request))
        return request.user == post.author


class CurrentUserCanVote(BasePermission):
    message = reason("user_cannot_vote")
    message_post_outside_stack = reason("user_cannot_see_object")
    message_already_voted = reason("user_has_already_voted_object")

    def has_object_permission(self, request: Request, view, obj) -> bool:
        if not request.user.stack.posts.filter(id=obj.id).exists():
            self.message = self.message_post_outside_stack
        elif Vote.objects.filter(user=request.user, post=obj).exists():
            self.message = self.message_already_voted
        else:
            return True

        return False


class CurrentUserIsNotBlocked(PostChildMixin, BasePermission):
    message = reason("user_is_blocked")
    accepted_methods = [*SAFE_METHODS, "DELETE"]

    def has_permission(self, request: Request, view):
        post = Post.objects.get(id=self.get_post_id(request))
        return (
            request.method in self.accepted_methods
            or post.is_anonymous
            or not post.author.blocked_users.filter(id=request.user.id).exists()
        )
