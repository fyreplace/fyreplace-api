from uuid import UUID

from grpc_interceptor.exceptions import InvalidArgument


def str_to_bool(string: str) -> bool:
    lowered = string.lower()

    if lowered == "true":
        return True
    elif lowered == "false":
        return False
    else:
        raise ValueError


def make_uuid(data: bytes) -> UUID:
    try:
        return UUID(bytes=data)
    except ValueError:
        raise InvalidArgument("invalid_uuid")
