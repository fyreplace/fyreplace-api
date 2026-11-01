package app.fyreplace.errors;

import jakarta.annotation.Nonnull;

public final class PermissionDeniedException extends ApiException {
    public PermissionDeniedException(@Nonnull String detail) {
        super(detail);
    }
}
