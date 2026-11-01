package app.fyreplace.grpc.endpoints.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.data.entities.User;
import app.fyreplace.grpc.endpoints.AccountTestsBase;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.UserCreation;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class CreateTests extends AccountTestsBase {
    private static final String CREATED_EMAIL = "created@example.org";
    private static final String INVALID_EMAIL = "invalid_email@example";

    @Test
    @RunOnVertxContext
    public void testValidUserCreation(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> accountService().create(creation("created_user", CREATED_EMAIL)), Empty.getDefaultInstance());
        asserter.assertThat(
                () -> Uni.createFrom().item(mailbox.getMailMessagesSentTo(CREATED_EMAIL)),
                mails -> assertEquals(1, mails.size()));
        asserter.assertThat(() -> user("created_user"), user -> {
            assertEquals(CREATED_EMAIL, user.getEmail());
            assertFalse(user.isActive());
            assertFalse(user.hasUsablePassword());
        });
    }

    @Test
    @RunOnVertxContext
    public void testExpiredPendingUserPurged(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> accountService()
                        .create(creation("stale_user", "stale@example.org"))
                        .chain(() -> user("stale_user"))
                        .flatMap(stale -> session(() -> userRepository.updateDateCreatedById(
                                stale.getId(),
                                OffsetDateTime.now().minus(User.LIFETIME).minusMinutes(1))))
                        .chain(() -> accountService().create(creation("fresh_user", "fresh@example.org")))
                        .chain(() -> session(() -> Uni.combine()
                                .all()
                                .unis(
                                        userRepository.existsByUsername("stale_user"),
                                        userRepository.existsByUsername("fresh_user"))
                                .asTuple())),
                tuple -> {
                    assertFalse(tuple.getItem1());
                    assertTrue(tuple.getItem2());
                });
    }

    @Test
    @RunOnVertxContext
    public void testShortestUsername(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> accountService().create(creation("abc", CREATED_EMAIL)), Empty.getDefaultInstance());
        asserter.assertThat(() -> user("abc"), user -> assertEquals(CREATED_EMAIL, user.getEmail()));
    }

    @Test
    @RunOnVertxContext
    public void testLongestUsername(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> accountService().create(creation("a".repeat(50), CREATED_EMAIL)), Empty.getDefaultInstance());
        asserter.assertThat(() -> user("a".repeat(50)), user -> assertEquals(CREATED_EMAIL, user.getEmail()));
    }

    @Test
    @RunOnVertxContext
    public void testReservedUsername(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("admins", CREATED_EMAIL)),
                Status.Code.PERMISSION_DENIED,
                "username_reserved");
    }

    @Test
    @RunOnVertxContext
    public void testReservedUsernameUppercase(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("Admins", CREATED_EMAIL)),
                Status.Code.PERMISSION_DENIED,
                "username_reserved");
    }

    @Test
    @RunOnVertxContext
    public void testReservedUsernameWithSeparators(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("a.d.m.i.n.s", CREATED_EMAIL)),
                Status.Code.PERMISSION_DENIED,
                "username_reserved");
    }

    @Test
    @RunOnVertxContext
    public void testReservedUsernameAndTakenEmail(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("admins", "user_0@example.org")),
                Status.Code.PERMISSION_DENIED,
                "username_reserved");
    }

    @Test
    @RunOnVertxContext
    public void testTakenEmail(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("created_user", "user_0@example.org")),
                Status.Code.ALREADY_EXISTS,
                "email_taken");
    }

    @Test
    @RunOnVertxContext
    public void testTakenEmailAndTakenUsername(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("user_0", "user_0@example.org")),
                Status.Code.ALREADY_EXISTS,
                "username_taken");
    }

    @Test
    @RunOnVertxContext
    public void testTakenEmailAndInvalidUsername(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("ab", "user_0@example.org")),
                Status.Code.INVALID_ARGUMENT,
                "invalid_username");
    }

    @Test
    @RunOnVertxContext
    public void testTakenUsername(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("user_0", CREATED_EMAIL)),
                Status.Code.ALREADY_EXISTS,
                "username_taken");
    }

    @Test
    @RunOnVertxContext
    public void testTakenUsernameAndInvalidEmail(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("user_0", INVALID_EMAIL)),
                Status.Code.ALREADY_EXISTS,
                "username_taken");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidUsername(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("ab", CREATED_EMAIL)),
                Status.Code.INVALID_ARGUMENT,
                "invalid_username");
    }

    @Test
    @RunOnVertxContext
    public void testEmptyUsername(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("", CREATED_EMAIL)),
                Status.Code.INVALID_ARGUMENT,
                "invalid_username");
    }

    @Test
    @RunOnVertxContext
    public void testTooLongUsername(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("a".repeat(51), CREATED_EMAIL)),
                Status.Code.INVALID_ARGUMENT,
                "invalid_username");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidEmail(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("created_user", INVALID_EMAIL)),
                Status.Code.INVALID_ARGUMENT,
                "invalid_email");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidEmailAndInvalidUsername(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("ab", INVALID_EMAIL)),
                Status.Code.INVALID_ARGUMENT,
                "invalid_username");
    }

    @Test
    @RunOnVertxContext
    public void testEmptyEmail(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("created_user", "")),
                Status.Code.INVALID_ARGUMENT,
                "invalid_email");
    }

    @Test
    @RunOnVertxContext
    public void testTooLongEmail(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("created_user", "a".repeat(250) + "@example.org")),
                Status.Code.INVALID_ARGUMENT,
                "invalid_email");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidEmailWithDoubleAt(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("created_user", "user@@example.org")),
                Status.Code.INVALID_ARGUMENT,
                "invalid_email");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidEmailWithSpace(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("created_user", "us er@example.org")),
                Status.Code.INVALID_ARGUMENT,
                "invalid_email");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidEmailWithTrailingDot(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().create(creation("created_user", "user@example.org.")),
                Status.Code.INVALID_ARGUMENT,
                "invalid_email");
    }

    private static @Nonnull UserCreation creation(@Nonnull String username, @Nonnull String email) {
        return UserCreation.newBuilder().setEmail(email).setUsername(username).build();
    }
}
