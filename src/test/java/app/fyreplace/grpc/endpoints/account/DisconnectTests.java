package app.fyreplace.grpc.endpoints.account;

import app.fyreplace.grpc.endpoints.AccountTestsBase;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Id;
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
public final class DisconnectTests extends AccountTestsBase {
    @Test
    @RunOnVertxContext
    public void testAuthenticatedRequest(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> user("user_0").flatMap(user -> accountService(token(user)).disconnect(Id.getDefaultInstance())),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().disconnect(Id.getDefaultInstance()),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
