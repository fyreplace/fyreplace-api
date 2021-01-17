from typing import Dict

from rest_framework.permissions import SAFE_METHODS, IsAuthenticated
from rest_framework.request import Request


def reason(name: str) -> Dict[str, str]:
    return {"reason": name}


class CurrentUserIsOwnerOrReadOnly(IsAuthenticated):
    message = reason("user_is_not_owner")

    def has_object_permission(self, request: Request, view, obj) -> bool:
        for field_name in ["user", "author", "issuer"]:
            if hasattr(obj, field_name):
                user = getattr(obj, field_name)
                return request.method in SAFE_METHODS or request.user == user

        return False
