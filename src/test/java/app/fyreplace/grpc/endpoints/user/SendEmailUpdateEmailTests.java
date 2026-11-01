package app.fyreplace.grpc.endpoints.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Email;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class SendEmailUpdateEmailTests extends UserTestsBase {
    private static final String NEW_EMAIL = "new_email@example.org";

    @Test
    @RunOnVertxContext
    public void testValid(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .sendEmailUpdateEmail(
                                        Email.newBuilder().setEmail(NEW_EMAIL).build())),
                Empty.getDefaultInstance());
        asserter.assertThat(
                () -> Uni.createFrom().item(mailbox.getMailMessagesSentTo(NEW_EMAIL)),
                mails -> assertEquals(1, mails.size()));
    }

    @Test
    @RunOnVertxContext
    public void testInvalidEmail(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .sendEmailUpdateEmail(
                                        Email.newBuilder().setEmail("invalid").build())),
                Status.Code.INVALID_ARGUMENT,
                "invalid_email");
    }

    @Test
    @RunOnVertxContext
    public void testEmailTaken(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .sendEmailUpdateEmail(Email.newBuilder()
                                        .setEmail("user_1@example.com")
                                        .build())),
                Status.Code.ALREADY_EXISTS,
                "email_taken");
    }

    @Test
    @RunOnVertxContext
    public void testOwnEmail(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .sendEmailUpdateEmail(Email.newBuilder()
                                        .setEmail("user_0@example.org")
                                        .build())),
                Status.Code.ALREADY_EXISTS,
                "email_taken");
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService()
                        .sendEmailUpdateEmail(
                                Email.newBuilder().setEmail(NEW_EMAIL).build()),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
