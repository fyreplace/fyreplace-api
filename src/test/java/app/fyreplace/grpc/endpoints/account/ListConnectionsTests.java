package app.fyreplace.grpc.endpoints.account;

import app.fyreplace.grpc.endpoints.AccountTestsBase;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Connections;
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
public final class ListConnectionsTests extends AccountTestsBase {
    @Test
    @RunOnVertxContext
    public void testAuthenticatedRequest(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> user("user_0")
                        .flatMap(user -> accountService(token(user)).listConnections(Empty.getDefaultInstance())),
                Connections.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService().listConnections(Empty.getDefaultInstance()),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
