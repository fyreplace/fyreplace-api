package app.fyreplace.grpc.endpoints.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.fyreplace.data.entities.MessagingToken;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.MessagingService;
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
public final class RegisterTokenTests extends NotificationTestsBase {
    @Test
    @RunOnVertxContext
    public void testRegisterToken(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(TARGET_USER)
                        .flatMap(u -> notificationService(token(u))
                                .registerToken(app.fyreplace.protos.MessagingToken.newBuilder()
                                        .setService(MessagingService.MESSAGING_SERVICE_APNS)
                                        .setToken("apns-token-user-2")
                                        .build())
                                .replaceWith(u))
                        .flatMap(u -> session(() -> messagingTokenRepository.findByTokenOrNull("apns-token-user-2"))
                                .map(token -> Tuple2.of(u.getId(), token))),
                pair -> {
                    assertEquals(pair.getItem1(), pair.getItem2().getUser().getId());
                    assertEquals(MessagingToken.Service.APNS, pair.getItem2().getService());
                });
    }

    @Test
    @RunOnVertxContext
    public void testRegisterReplacesOwnToken(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(TARGET_USER)
                        .flatMap(u -> notificationService(token(u))
                                .registerToken(app.fyreplace.protos.MessagingToken.newBuilder()
                                        .setService(MessagingService.MESSAGING_SERVICE_APNS)
                                        .setToken("first-token-user-2")
                                        .build())
                                .chain(() -> notificationService(token(u))
                                        .registerToken(app.fyreplace.protos.MessagingToken.newBuilder()
                                                .setService(MessagingService.MESSAGING_SERVICE_FCM)
                                                .setToken("second-token-user-2")
                                                .build()))
                                .replaceWith(u))
                        .flatMap(u -> session(() ->
                                messagingTokenRepository.findByUserId(u.getId()).list())),
                tokens -> {
                    assertEquals(1, tokens.size());
                    assertEquals("second-token-user-2", tokens.getFirst().getToken());
                    assertEquals(MessagingToken.Service.FCM, tokens.getFirst().getService());
                });
    }

    @Test
    @RunOnVertxContext
    public void testTokenTakenByOtherUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(TARGET_USER)
                        .flatMap(u -> notificationService(token(u))
                                .registerToken(app.fyreplace.protos.MessagingToken.newBuilder()
                                        .setService(MessagingService.MESSAGING_SERVICE_APNS)
                                        .setToken("fcm-token-user-1")
                                        .build())),
                Status.Code.ALREADY_EXISTS,
                "token_taken");
    }

    @Test
    @RunOnVertxContext
    public void testUnspecifiedService(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(TARGET_USER)
                        .flatMap(u -> notificationService(token(u))
                                .registerToken(app.fyreplace.protos.MessagingToken.newBuilder()
                                        .setService(MessagingService.MESSAGING_SERVICE_UNSPECIFIED)
                                        .setToken("some-token")
                                        .build())),
                Status.Code.INVALID_ARGUMENT,
                "invalid_service");
    }

    @Test
    @RunOnVertxContext
    public void testEmptyToken(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(TARGET_USER)
                        .flatMap(u -> notificationService(token(u))
                                .registerToken(app.fyreplace.protos.MessagingToken.newBuilder()
                                        .setService(MessagingService.MESSAGING_SERVICE_FCM)
                                        .setToken("")
                                        .build())),
                Status.Code.INVALID_ARGUMENT,
                "invalid_token");
    }

    @Test
    @RunOnVertxContext
    public void testTooLongToken(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(TARGET_USER)
                        .flatMap(u -> notificationService(token(u))
                                .registerToken(app.fyreplace.protos.MessagingToken.newBuilder()
                                        .setService(MessagingService.MESSAGING_SERVICE_APNS)
                                        .setToken("a".repeat(MessagingToken.TOKEN_MAX_LENGTH + 1))
                                        .build())),
                Status.Code.INVALID_ARGUMENT,
                "invalid_token");
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> notificationService()
                        .registerToken(app.fyreplace.protos.MessagingToken.newBuilder()
                                .setService(MessagingService.MESSAGING_SERVICE_APNS)
                                .setToken("some-token")
                                .build()),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
