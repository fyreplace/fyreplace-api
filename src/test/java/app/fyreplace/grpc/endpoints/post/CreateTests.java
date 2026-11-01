package app.fyreplace.grpc.endpoints.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import com.google.protobuf.Empty;
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
public final class CreateTests extends PostTestsBase {
    @Test
    @RunOnVertxContext
    public void testCreate(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .create(Empty.getDefaultInstance())
                                .flatMap(id -> session(() -> postRepository.findById(uuid(id)))
                                        .map(post -> Tuple2.of(active, post)))),
                pair -> {
                    assertEquals(
                            pair.getItem1().getId(), pair.getItem2().getAuthor().getId());
                    assertFalse(pair.getItem2().isPublished());
                    assertFalse(pair.getItem2().isDeleted());
                });
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter, () -> postService().create(Empty.getDefaultInstance()), Status.Code.UNAUTHENTICATED, null);
    }
}
