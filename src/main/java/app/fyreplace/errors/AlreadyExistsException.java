package app.fyreplace.errors;

import jakarta.annotation.Nonnull;

public final class AlreadyExistsException extends ApiException {
    public AlreadyExistsException(@Nonnull String detail) {
        super(detail);
    }
}
