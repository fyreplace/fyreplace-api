package app.fyreplace.errors;

import jakarta.annotation.Nonnull;

public final class CancelledException extends ApiException {
    public CancelledException(@Nonnull String detail) {
        super(detail);
    }
}
