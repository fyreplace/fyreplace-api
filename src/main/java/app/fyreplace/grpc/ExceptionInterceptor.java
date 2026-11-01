package app.fyreplace.grpc;

import app.fyreplace.errors.AlreadyExistsException;
import app.fyreplace.errors.ApiException;
import app.fyreplace.errors.CancelledException;
import app.fyreplace.errors.InternalException;
import app.fyreplace.errors.InvalidArgumentException;
import app.fyreplace.errors.NotFoundException;
import app.fyreplace.errors.PermissionDeniedException;
import app.fyreplace.errors.UnauthenticatedException;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.grpc.ExceptionHandler;
import io.quarkus.grpc.ExceptionHandlerProvider;
import io.quarkus.security.UnauthorizedException;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;

@ApplicationScoped
public final class ExceptionInterceptor implements ExceptionHandlerProvider {
    @Override
    public <I, O> @Nonnull ExceptionHandler<I, O> createHandler(
            @Nonnull ServerCall.Listener<I> listener, @Nonnull ServerCall<I, O> call, @Nonnull Metadata headers) {
        return new ApiExceptionListener<>(listener, call, headers);
    }

    @Override
    public @Nonnull Throwable transform(@Nonnull Throwable failure) {
        return toStatusException(failure);
    }

    private static @Nonnull Throwable toStatusException(@Nonnull Throwable failure) {
        if (failure instanceof ApiException exception) {
            return status(exception).withDescription(exception.getDetail()).asRuntimeException();
        } else if (failure instanceof UnauthorizedException) {
            return toStatusException(new UnauthenticatedException("missing_credentials"));
        } else if (failure instanceof NoResultException) {
            return toStatusException(new NotFoundException());
        } else {
            return ExceptionHandlerProvider.toStatusException(failure, true);
        }
    }

    private static @Nonnull Status status(@Nonnull ApiException failure) {
        return switch (failure) {
            case UnauthenticatedException _ -> Status.UNAUTHENTICATED;
            case PermissionDeniedException _ -> Status.PERMISSION_DENIED;
            case NotFoundException _ -> Status.NOT_FOUND;
            case InvalidArgumentException _ -> Status.INVALID_ARGUMENT;
            case AlreadyExistsException _ -> Status.ALREADY_EXISTS;
            case CancelledException _ -> Status.CANCELLED;
            case InternalException _ -> Status.INTERNAL;
        };
    }

    private static final class ApiExceptionListener<I, O> extends ExceptionHandler<I, O> {
        ApiExceptionListener(
                @Nonnull ServerCall.Listener<I> listener, @Nonnull ServerCall<I, O> call, @Nonnull Metadata headers) {
            super(listener, call, headers);
        }

        @Override
        protected void handleException(
                @Nonnull Throwable failure, @Nonnull ServerCall<I, O> call, @Nonnull Metadata headers) {
            final var converted = toStatusException(failure);

            if (converted instanceof StatusRuntimeException convertedStatus) {
                call.close(
                        convertedStatus.getStatus(),
                        convertedStatus.getTrailers() != null ? convertedStatus.getTrailers() : headers);
            }
        }
    }
}
