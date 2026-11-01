package app.fyreplace.grpc.endpoints.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.fyreplace.data.entities.User;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Bio;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class UpdateBioTests extends UserTestsBase {
    @Test
    @RunOnVertxContext
    public void testValid(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .updateBio(
                                        Bio.newBuilder().setBio("Hello world").build())),
                Empty.getDefaultInstance());
        asserter.assertThat(() -> user(ACTIVE_USER), active -> assertEquals("Hello world", active.getBio()));
    }

    @Test
    @RunOnVertxContext
    public void testEmpty(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .updateBio(Bio.newBuilder().setBio("").build())),
                Empty.getDefaultInstance());
        asserter.assertThat(() -> user(ACTIVE_USER), active -> assertEquals("", active.getBio()));
    }

    @Test
    @RunOnVertxContext
    public void testBoundaryLength(@Nonnull UniAsserter asserter) {
        final var bio = "a".repeat(User.BIO_MAX_LENGTH);
        asserter.assertEquals(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .updateBio(Bio.newBuilder().setBio(bio).build())),
                Empty.getDefaultInstance());
        asserter.assertThat(() -> user(ACTIVE_USER), active -> assertEquals(bio, active.getBio()));
    }

    @Test
    @RunOnVertxContext
    public void testTooLong(@Nonnull UniAsserter asserter) {
        final var bio = "a".repeat(User.BIO_MAX_LENGTH + 1);
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .updateBio(Bio.newBuilder().setBio(bio).build())),
                Status.Code.INVALID_ARGUMENT,
                "invalid_bio");
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService().updateBio(Bio.newBuilder().setBio("Hello").build()),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
