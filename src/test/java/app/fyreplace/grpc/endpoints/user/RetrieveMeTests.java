package app.fyreplace.grpc.endpoints.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
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
public final class RetrieveMeTests extends UserTestsBase {
    @Test
    @RunOnVertxContext
    public void testValid(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active)).retrieveMe(Empty.getDefaultInstance())),
                me -> {
                    assertEquals(ACTIVE_USER, me.getProfile().getUsername());
                    assertEquals("user_0@example.org", me.getEmail());
                    assertEquals("I am user 0", me.getBio());
                });
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService().retrieveMe(Empty.getDefaultInstance()),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testInvalidBearerToken(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService("invalid").retrieveMe(Empty.getDefaultInstance()),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
