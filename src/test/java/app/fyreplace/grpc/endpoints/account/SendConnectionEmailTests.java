package app.fyreplace.grpc.endpoints.account;

import app.fyreplace.grpc.endpoints.AccountTestsBase;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Email;
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
public final class SendConnectionEmailTests extends AccountTestsBase {
    @Test
    @RunOnVertxContext
    public void testPasswordlessUser(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> accountService().sendConnectionEmail(email("passwordless_user@example.org")),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testPasswordUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().sendConnectionEmail(email("password_user@example.org")),
                Status.Code.CANCELLED,
                "caller_has_password");
    }

    @Test
    @RunOnVertxContext
    public void testPendingUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().sendConnectionEmail(email("pending_user@example.org")),
                Status.Code.PERMISSION_DENIED,
                "caller_pending");
    }

    @Test
    @RunOnVertxContext
    public void testBannedUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().sendConnectionEmail(email("banned_user@example.org")),
                Status.Code.PERMISSION_DENIED,
                "caller_banned");
    }

    @Test
    @RunOnVertxContext
    public void testPermanentlyBannedUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().sendConnectionEmail(email("permanent_banned_user@example.org")),
                Status.Code.PERMISSION_DENIED,
                "caller_banned_permanently");
    }

    @Test
    @RunOnVertxContext
    public void testDeletedUser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().sendConnectionEmail(email("deleted_user@example.org")),
                Status.Code.PERMISSION_DENIED,
                "caller_deleted");
    }

    @Test
    @RunOnVertxContext
    public void testUnknownValidEmail(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().sendConnectionEmail(email("unknown@example.org")),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testUnknownInvalidEmail(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().sendConnectionEmail(email("unknown@example")),
                Status.Code.INVALID_ARGUMENT,
                "invalid_email");
    }

    private static @Nonnull Email email(@Nonnull String address) {
        return Email.newBuilder().setEmail(address).build();
    }
}
