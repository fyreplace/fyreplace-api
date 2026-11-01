package app.fyreplace.errors;

import jakarta.annotation.Nonnull;

public final class InvalidArgumentException extends ApiException {
    public InvalidArgumentException(@Nonnull String detail) {
        super(detail);
    }
}
