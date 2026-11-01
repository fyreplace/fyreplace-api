package app.fyreplace.grpc.endpoints.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
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
public final class CountTests extends NotificationTestsBase {
    @Test
    @RunOnVertxContext
    public void testCount(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(u -> notificationService(token(u)).count(Empty.getDefaultInstance())),
                count -> assertEquals(2, count.getCount()));
    }

    @Test
    @RunOnVertxContext
    public void testCountWithoutSubscriptions(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(u -> notificationService(token(u)).count(Empty.getDefaultInstance())),
                count -> assertEquals(0, count.getCount()));
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> notificationService().count(Empty.getDefaultInstance()),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testSelfCommentNotCounted(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .create(app.fyreplace.protos.CommentCreation.newBuilder()
                                        .setPostId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setText("own comment")
                                        .build())
                                .replaceWith(pair))
                        .flatMap(pair ->
                                notificationService(token(pair.getItem1())).count(Empty.getDefaultInstance())),
                count -> assertEquals(0, count.getCount()));
    }

    @Test
    @RunOnVertxContext
    public void testOtherCommentCounted(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> user(TARGET_USER)
                                .flatMap(target -> commentService(token(target))
                                        .create(app.fyreplace.protos.CommentCreation.newBuilder()
                                                .setPostId(toProtoService.byteString(
                                                        pair.getItem2().getId()))
                                                .setText("other comment")
                                                .build())
                                        .replaceWith(pair)))
                        .flatMap(pair ->
                                notificationService(token(pair.getItem1())).count(Empty.getDefaultInstance())),
                count -> assertEquals(3, count.getCount()));
    }
}
