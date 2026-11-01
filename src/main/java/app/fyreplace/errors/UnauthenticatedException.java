package app.fyreplace.errors;

import jakarta.annotation.Nonnull;

public final class UnauthenticatedException extends ApiException {
    public UnauthenticatedException(@Nonnull String detail) {
        super(detail);
    }
}
