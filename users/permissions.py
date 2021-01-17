from rest_framework.permissions import SAFE_METHODS, BasePermission, IsAuthenticated
from rest_framework.request import Request

from core.permissions import reason


class CurrentUserIsAlive(IsAuthenticated):
    message = reason("user_is_not_alive")

    def has_permission(self, request: Request, view) -> bool:
        return (
            super().has_permission(request, view)
            and not request.user.is_deleted
            and not request.user.is_banned
        )


class CurrentUserIsActive(CurrentUserIsAlive):
    message = reason("user_is_not_active")

    def has_permission(self, request: Request, view) -> bool:
        return super().has_permission(request, view) and request.user.is_active


class CurrentUserIsPending(CurrentUserIsAlive):
    message = reason("user_is_not_pending")

    def has_permission(self, request: Request, view) -> bool:
        return super().has_permission(request, view) and not request.user.is_active


class ObjectIsCurrentUser(BasePermission):
    message = reason("object_is_not_user")

    def has_object_permission(self, request: Request, view, obj) -> bool:
        return request.method in SAFE_METHODS or request.user == obj


class ObjectIsNotCurrentUser(BasePermission):
    message = reason("object_is_current_user")

    def has_object_permission(self, request: Request, view, obj) -> bool:
        return request.method in SAFE_METHODS or request.user != obj
