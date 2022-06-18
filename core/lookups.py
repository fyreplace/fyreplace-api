from typing import Any

from django.db.models.lookups import Exact

from .utils import make_uuid


class BytesLookup(Exact):
    lookup_name = "bytes"

    def __init__(self, lhs: Any, rhs: Any):
        if isinstance(rhs, bytes):
            rhs = make_uuid(data=rhs) if len(rhs) > 0 else ""

        super().__init__(lhs, rhs)

    def get_rhs_op(self, connection: Any, rhs: Any) -> Any:
        self.lookup_name = super().lookup_name
        result = super().get_rhs_op(connection, rhs)
        self.lookup_name = "bytes"
        return result
