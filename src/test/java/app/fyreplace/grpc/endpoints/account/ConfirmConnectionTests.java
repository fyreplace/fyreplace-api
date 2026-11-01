package app.fyreplace.grpc.endpoints.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.data.entities.User;
import app.fyreplace.grpc.endpoints.AccountTestsBase;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.ConnectionToken;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class ConfirmConnectionTests extends AccountTestsBase {
    @Test
    @RunOnVertxContext
    public void testValidToken(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user("passwordless_user")
                        .flatMap(user -> accountService()
                                .confirmConnection(connectionToken(jwtService.makeJwt(user, true)))
                                .map(response -> jwtService.verifyToken(response.getToken()))
                                .flatMap(claims -> user("passwordless_user").map(userId -> Tuple2.of(userId, claims)))),
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
    public void testPendingUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user("pending_user")
                        .flatMap(user ->
                                accountService().confirmConnection(connectionToken(jwtService.makeJwt(user, true)))),
                Status.Code.PERMISSION_DENIED,
                "caller_pending");
    }

    @Test
    @RunOnVertxContext
    public void testDeletedUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user("deleted_user")
                        .flatMap(user ->
                                accountService().confirmConnection(connectionToken(jwtService.makeJwt(user, true)))),
                Status.Code.PERMISSION_DENIED,
                "caller_deleted");
    }

    @Test
    @RunOnVertxContext
    public void testBannedUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user("banned_user")
                        .flatMap(user ->
                                accountService().confirmConnection(connectionToken(jwtService.makeJwt(user, true)))),
                Status.Code.PERMISSION_DENIED,
                "caller_banned");
    }

    @Test
    @RunOnVertxContext
    public void testPermanentlyBannedUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user("permanent_banned_user")
                        .flatMap(user ->
                                accountService().confirmConnection(connectionToken(jwtService.makeJwt(user, true)))),
                Status.Code.PERMISSION_DENIED,
                "caller_banned_permanently");
    }

    @Test
    @RunOnVertxContext
    public void testUntypedToken(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user("passwordless_user")
                        .flatMap(user ->
                                accountService().confirmConnection(connectionToken(jwtService.makeJwt(user, false)))),
                Status.Code.UNAUTHENTICATED,
                "invalid_token");
    }

    @Test
    @RunOnVertxContext
    public void testStaleToken(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user("passwordless_user")
                        .flatMap(user -> accountService().confirmConnection(connectionToken(staleToken(user)))),
                Status.Code.UNAUTHENTICATED,
                "timestamp_exceeded");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidToken(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().confirmConnection(connectionToken("invalid")),
                Status.Code.UNAUTHENTICATED,
                "invalid_token");
    }

    private static @Nonnull ConnectionToken connectionToken(@Nullable String token) {
        return ConnectionToken.newBuilder().setToken(token).build();
    }

    private static @Nonnull String staleToken(@Nonnull User user) {
        return Jwt.issuer(ConfigProvider.getConfig().getValue("app.url", String.class))
                .subject(user.id.toString())
                .claim("user_id", user.id.toString())
                .claim("timestamp", Instant.now().minus(2, ChronoUnit.DAYS).getEpochSecond())
                .expiresIn(Duration.ofDays(7))
                .sign();
    }
}
