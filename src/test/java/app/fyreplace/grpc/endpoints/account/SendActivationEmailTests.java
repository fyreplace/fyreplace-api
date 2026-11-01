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
public final class SendActivationEmailTests extends AccountTestsBase {
    @Test
    @RunOnVertxContext
    public void testPendingEmail(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> accountService().sendActivationEmail(email("pending_user@example.org")),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testActiveEmail(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> accountService().sendActivationEmail(email("user_0@example.org")), Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testUnknownEmail(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().sendActivationEmail(email("unknown@example.org")),
                Status.Code.NOT_FOUND,
                null);
    }

    private static @Nonnull Email email(@Nonnull String address) {
        return Email.newBuilder().setEmail(address).build();
    }
}
