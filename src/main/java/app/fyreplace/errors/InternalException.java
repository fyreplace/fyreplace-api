package app.fyreplace.errors;

import jakarta.annotation.Nonnull;

public final class InternalException extends ApiException {
    public InternalException() {
        super(null);
    }

    public InternalException(@Nonnull Throwable cause) {
        super(null, cause);
    }
}
