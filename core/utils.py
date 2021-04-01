def str_to_bool(string: str) -> bool:
    lowered = string.lower()

    if lowered == "true":
        return True
    elif lowered == "false":
        return False
    else:
        raise ValueError
