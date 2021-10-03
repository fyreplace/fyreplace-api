import re
from abc import ABC, abstractmethod
from typing import Callable, Iterable, Iterator, List, Optional, Type

from django.conf import settings
from django.db.models import Model, QuerySet
from django.db.models.query_utils import Q
from google.protobuf.message import Message
from grpc_interceptor.exceptions import InvalidArgument

from core.models import MessageConvertible
from protos import pagination_pb2


class PaginationAdapter(ABC):
    def __init__(self, query: QuerySet):
        self.initial_query = query.order_by(*self.get_cursor_fields())
        self.query = self.initial_query
        self.forward = True

    @abstractmethod
    def get_cursor_fields(self) -> Iterable[str]:
        raise NotImplementedError

    def apply_header(self, header: pagination_pb2.Header):
        self.forward = header.forward

    def make_queryset_filters(self, page: pagination_pb2.Page) -> Q:
        filters = Q()
        equalities = {}

        if self.forward == page.cursor.is_next:
            comp_normal = "gt"
            comp_reverse = "lt"
        else:
            comp_normal = "lt"
            comp_reverse = "gt"

        for pair in page.cursor.data:
            comparator = comp_reverse if pair.key.startswith("-") else comp_normal
            stuff = {
                **equalities,
                pair.key.removeprefix("-") + "__" + comparator: pair.value,
            }
            filters |= Q(**stuff)
            equalities[pair.key] = pair.value

        return filters

    def make_cursor_data(self, item: Model) -> List[pagination_pb2.KeyValuePair]:
        return [
            pagination_pb2.KeyValuePair(key=field, value=str(getattr(item, field)))
            for field in self.get_cursor_fields()
        ]

    def make_message(self, item: Model, **overrides) -> Message:
        if isinstance(item, MessageConvertible):
            return item.to_message(**overrides)
        else:
            raise ValueError


class PaginatorMixin:
    def paginate(
        self,
        request_iterator: Iterator[pagination_pb2.Page],
        bundle_class: Type,
        adapter: PaginationAdapter,
        message_overrides: dict = {},
        on_items: Optional[Callable[[list], None]] = None,
    ):
        bundle_field = re.sub(r"(?<!^)(?=[A-Z])", "_", bundle_class.__name__).lower()
        header_received = False
        size = 0

        for request in request_iterator:
            previous_cursor = None
            next_cursor = None
            position_type = request.WhichOneof("position")
            is_header = position_type == "header"
            has_previous = False
            has_next = False

            if is_header:
                header_received = True
                size = request.header.size
                adapter.apply_header(request.header)

                if not (0 < size <= settings.PAGINATION_MAX_SIZE):
                    raise InvalidArgument("invalid_size")

                continue
            elif not header_received:
                raise InvalidArgument("missing_header")

            items = adapter.query
            filters = adapter.make_queryset_filters(request)

            if not adapter.forward:
                items = items.reverse()

            items = items.filter(filters).select_related()[: size + 1]

            if len(request.cursor.data) > 0:
                if request.cursor.is_next:
                    has_previous = True
                else:
                    has_next = True

            items = list(items)
            item_count = len(items)

            if item_count == 0:
                yield bundle_class(**{bundle_field: []})
                continue

            if item_count > size:
                items.pop()
                item_count -= 1

                if request.cursor.is_next:
                    has_next = True
                else:
                    has_previous = True

            if has_previous:
                previous_cursor = pagination_pb2.Cursor(
                    data=adapter.make_cursor_data(items[0]), is_next=False
                )

            if has_next:
                next_cursor = pagination_pb2.Cursor(
                    data=adapter.make_cursor_data(items[-1]), is_next=True
                )

            if on_items:
                on_items(items)

            yield bundle_class(
                **{
                    bundle_field: [
                        adapter.make_message(p, **message_overrides) for p in items
                    ],
                    "previous": previous_cursor,
                    "next": next_cursor,
                }
            )
