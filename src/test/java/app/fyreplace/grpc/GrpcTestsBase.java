package app.fyreplace.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import app.fyreplace.TestsBase;
import app.fyreplace.data.entities.User;
import app.fyreplace.protos.Id;
import app.fyreplace.services.FromProtoService;
import app.fyreplace.services.JwtService;
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractStub;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;

public abstract class GrpcTestsBase extends TestsBase {
    private static volatile ManagedChannel channel;

    @Inject
    protected User.Repository userRepository;

    @Inject
    protected FromProtoService fromProtoService;

    @Inject
    protected JwtService jwtService;

    protected static synchronized @Nonnull ManagedChannel channel() {
        if (channel == null) {
            final var port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
            channel = ManagedChannelBuilder.forAddress("localhost", port)
                    .usePlaintext()
                    .build();
        }

        return channel;
    }

    @AfterAll
    public static void tearDownChannel() {
        final var current = channel;

        if (current != null) {
            channel = null;
            current.shutdown();
        }
    }

    protected static <S extends AbstractStub<S>> @Nonnull S withToken(@Nonnull S stub, @Nullable String token) {
        return token == null ? stub : stub.withCallCredentials(new Token(token));
    }

    protected static <T> void assertFailure(
            @Nonnull UniAsserter asserter,
            @Nonnull Supplier<Uni<T>> call,
            @Nonnull Status.Code code,
            @Nullable String detail) {
        asserter.assertFailedWith(call, failure -> {
            final var status =
                    assertInstanceOf(StatusRuntimeException.class, failure).getStatus();
            assertEquals(code, status.getCode(), failure.toString());

            if (detail != null) {
                assertEquals(detail, status.getDescription());
            }
        });
    }

    protected final @Nonnull Uni<User> user(@Nonnull String username) {
        return session(() -> userRepository.findByUsername(username));
    }

    protected final @Nonnull String token(@Nonnull User user) {
        return jwtService.makeJwt(user, false);
    }

    protected final @Nonnull UUID uuid(@Nonnull Id id) {
        return fromProtoService.uuid(id.getId());
    }

    private static final class Token extends CallCredentials {
        private final @Nonnull String token;

        private Token(@Nonnull String token) {
            this.token = token;
        }

        @Override
        public void applyRequestMetadata(
                @Nonnull CallCredentials.RequestInfo requestInfo,
                @Nonnull Executor executor,
                @Nonnull CallCredentials.MetadataApplier applier) {
            final var metadata = new Metadata();
            metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
            applier.apply(metadata);
        }
    }
}
