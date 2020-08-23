from rest_framework.permissions import SAFE_METHODS, BasePermission, IsAuthenticated
from rest_framework.request import Request


class CurrentUserIsAlive(IsAuthenticated):
    def has_permission(self, request: Request, view) -> bool:
        return (
            super().has_permission(request, view)
            and not request.user.is_deleted
            and not request.user.is_banned
        )


class CurrentUserIsActive(CurrentUserIsAlive):
    def has_permission(self, request: Request, view) -> bool:
        return super().has_permission(request, view) and request.user.is_active


class CurrentUserIsPending(CurrentUserIsAlive):
    def has_permission(self, request: Request, view) -> bool:
        return super().has_permission(request, view) and not request.user.is_active


class ObjectIsCurrentUser(BasePermission):
    def has_object_permission(self, request: Request, view, obj) -> bool:
        return request.method in SAFE_METHODS or request.user == obj


class ObjectIsNotCurrentUser(BasePermission):
    def has_object_permission(self, request: Request, view, obj) -> bool:
        return request.method in SAFE_METHODS or request.user != obj
