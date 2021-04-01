from typing import Callable


def no_auth(func: Callable) -> Callable:
    func.__dict__["no_auth"] = True
    return func
