package app.fyreplace.grpc.endpoints.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Token;
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
public final class ConfirmEmailUpdateTests extends UserTestsBase {
    private static final String NEW_EMAIL = "confirmed_new@example.org";

    @Test
    @RunOnVertxContext
    public void testValid(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> joinAsTuple(user(ACTIVE_USER), user(ACTIVE_USER))
                        .flatMap(pair -> userService(token(pair.getItem1()))
                                .confirmEmailUpdate(Token.newBuilder()
                                        .setToken(emailToken(pair.getItem2(), NEW_EMAIL))
                                        .build())),
                Empty.getDefaultInstance());
        asserter.assertThat(() -> user(ACTIVE_USER), active -> assertEquals(NEW_EMAIL, active.getEmail()));
    }

    @Test
    @RunOnVertxContext
    public void testOtherUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER))
                        .flatMap(pair -> userService(token(pair.getItem1()))
                                .confirmEmailUpdate(Token.newBuilder()
                                        .setToken(emailToken(pair.getItem2(), NEW_EMAIL))
                                        .build())),
                Status.Code.PERMISSION_DENIED,
                "invalid_user");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidToken(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .confirmEmailUpdate(
                                        Token.newBuilder().setToken("invalid").build())),
                Status.Code.UNAUTHENTICATED,
                "invalid_token");
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService()
                        .confirmEmailUpdate(Token.newBuilder().setToken("any").build()),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
