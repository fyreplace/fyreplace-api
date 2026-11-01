package app.fyreplace.grpc.endpoints.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
public final class ClearTests extends NotificationTestsBase {
    @Test
    @RunOnVertxContext
    public void testClearMarksEverythingRead(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(u -> postOf(ACTIVE_USER, true, 1)
                                .flatMap(post -> notificationService(token(u))
                                        .clear(Empty.getDefaultInstance())
                                        .replaceWith(post))
                                .flatMap(post -> joinAsTuple(
                                        session(() -> subscriptionRepository.findByUserIdAndPostIdOrNull(
                                                u.getId(), post.getId())),
                                        commentOf(post, 2)))
                                .flatMap(pair -> notificationService(token(u))
                                        .count(Empty.getDefaultInstance())
                                        .map(count -> Tuple2.of(
                                                pair.getItem1()
                                                        .getLastCommentSeen()
                                                        .getId(),
                                                Tuple2.of(pair.getItem2().getId(), count.getCount()))))),
                pair -> {
                    assertEquals(pair.getItem2().getItem1(), pair.getItem1());
                    assertEquals(0, pair.getItem2().getItem2());
                });
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> notificationService().clear(Empty.getDefaultInstance()),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
