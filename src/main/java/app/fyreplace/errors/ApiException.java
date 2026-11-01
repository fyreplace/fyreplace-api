package app.fyreplace.errors;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract sealed class ApiException extends RuntimeException
        permits UnauthenticatedException,
                PermissionDeniedException,
                NotFoundException,
                InvalidArgumentException,
                AlreadyExistsException,
                CancelledException,
                InternalException {
    protected ApiException(@Nullable String detail) {
        super(detail);
    }

    protected ApiException(@Nullable String detail, @Nonnull Throwable cause) {
        super(detail, cause);
    }

    public final @Nullable String getDetail() {
        return getMessage();
    }
}
