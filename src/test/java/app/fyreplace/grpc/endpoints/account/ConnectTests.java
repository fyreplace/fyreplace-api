package app.fyreplace.grpc.endpoints.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.grpc.endpoints.AccountTestsBase;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.ConnectionCredentials;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class ConnectTests extends AccountTestsBase {
    @Test
    @RunOnVertxContext
    public void testValidCredentials(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user("password_user")
                        .flatMap(user -> accountService()
                                .connect(credentials("password_user@example.org", "password123"))
                                .map(response -> jwtService.verifyToken(response.getToken()))
                                .flatMap(claims -> user("password_user").map(userId -> Tuple2.of(userId, claims)))),
                pair -> {
                    final var expectedUserId = pair.getItem1().id.toString();
                    final var claims = pair.getItem2();

                    assertNotNull(claims);
                    assertEquals(expectedUserId, claims.getSubject());
                    assertEquals(expectedUserId, claims.<String>claim("user_id").orElse(null));
                    assertTrue(claims.<Number>claim("timestamp").isEmpty());
                });
    }

    @Test
    @RunOnVertxContext
    public void testWrongPassword(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().connect(credentials("password_user@example.org", "wrong-password")),
                Status.Code.INVALID_ARGUMENT,
                "invalid_password");
    }

    @Test
    @RunOnVertxContext
    public void testUnknownEmail(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().connect(credentials("unknown@example.org", "password123")),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testPasswordlessUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().connect(credentials("passwordless_user@example.org", "password123")),
                Status.Code.INVALID_ARGUMENT,
                "invalid_password");
    }

    @Test
    @RunOnVertxContext
    public void testPendingUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().connect(credentials("pending_user@example.org", "password123")),
                Status.Code.PERMISSION_DENIED,
                "caller_pending");
    }

    @Test
    @RunOnVertxContext
    public void testDeletedUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().connect(credentials("deleted_user@example.org", "password123")),
                Status.Code.PERMISSION_DENIED,
                "caller_deleted");
    }

    @Test
    @RunOnVertxContext
    public void testBannedUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().connect(credentials("banned_user@example.org", "password123")),
                Status.Code.PERMISSION_DENIED,
                "caller_banned");
    }

    @Test
    @RunOnVertxContext
    public void testPermanentlyBannedUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().connect(credentials("permanent_banned_user@example.org", "password123")),
                Status.Code.PERMISSION_DENIED,
                "caller_banned_permanently");
    }

    private static @Nonnull ConnectionCredentials credentials(@Nonnull String email, @Nonnull String password) {
        return ConnectionCredentials.newBuilder()
                .setEmail(email)
                .setPassword(password)
                .build();
    }
}
