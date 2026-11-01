package app.fyreplace.grpc.endpoints.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Header;
import app.fyreplace.protos.Page;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class ListTests extends NotificationTestsBase {
    @Test
    @RunOnVertxContext
    public void testList(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> notificationService(token(pair.getItem1()))
                                .list(paginateRequest(10, 0))
                                .collect()
                                .asList()
                                .map(bundles -> Tuple2.of(pair.getItem2(), bundles))),
                pair -> {
                    assertEquals(1, pair.getItem2().size());
                    final var notifications = pair.getItem2().getFirst().getNotificationsList();
                    assertEquals(1, notifications.size());
                    final var notification = notifications.get(0);
                    assertEquals(2, notification.getCount());
                    assertFalse(notification.getIsFlag());
                    assertTrue(notification.hasPost());
                    assertTrue(notification.getPost().getIsPreview());
                    assertEquals(
                            toProtoService.byteString(pair.getItem1().getId()),
                            notification.getPost().getId());
                });
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> notificationService()
                        .list(paginateRequest(10, 0))
                        .collect()
                        .asList(),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testPagination(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> createSubscribedPosts(active, 3)
                                .chain(() -> notificationService(token(active))
                                        .list(paginateRequest(2, 0, 1))
                                        .collect()
                                        .asList())),
                bundles -> {
                    assertEquals(2, bundles.size());
                    assertEquals(2, bundles.get(0).getNotificationsCount());
                    assertTrue(bundles.get(0).hasNext());
                    assertEquals(1, bundles.get(1).getNotificationsCount());
                    assertTrue(bundles.get(1).hasPrevious());
                    assertFalse(bundles.get(1).hasNext());
                });
    }

    @Test
    @RunOnVertxContext
    public void testReverse(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> createSubscribedPosts(active, 2)
                                .chain(() -> Uni.combine()
                                        .all()
                                        .unis(
                                                notificationService(token(active))
                                                        .list(paginateRequest(false, 10, 0))
                                                        .collect()
                                                        .asList(),
                                                notificationService(token(active))
                                                        .list(paginateRequest(true, 10, 0))
                                                        .collect()
                                                        .asList())
                                        .asTuple())),
                tuple -> {
                    final var backward = tuple.getItem1().getFirst().getNotificationsList();
                    final var forward = tuple.getItem2().getFirst().getNotificationsList();
                    assertEquals(2, backward.size());
                    assertEquals(
                            backward.get(0).getPost().getId(),
                            forward.get(1).getPost().getId());
                });
    }

    @Test
    @RunOnVertxContext
    public void testOutOfBounds(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(other -> notificationService(token(other))
                                .list(paginateRequest(10, 5))
                                .collect()
                                .asList()),
                bundles -> {
                    assertEquals(1, bundles.size());
                    assertEquals(0, bundles.getFirst().getNotificationsCount());
                    assertFalse(bundles.getFirst().hasNext());
                });
    }

    @Test
    @RunOnVertxContext
    public void testInvalidSize(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(OTHER_USER)
                        .flatMap(other -> notificationService(token(other))
                                .list(Multi.createFrom()
                                        .items(Page.newBuilder()
                                                .setHeader(Header.newBuilder()
                                                        .setForward(true)
                                                        .setSize(51))
                                                .build()))
                                .collect()
                                .asList()),
                Status.Code.INVALID_ARGUMENT,
                "invalid_size");
    }

    @Test
    @RunOnVertxContext
    public void testMissingHeader(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(OTHER_USER)
                        .flatMap(other -> notificationService(token(other))
                                .list(Multi.createFrom()
                                        .items(Page.newBuilder()
                                                .setCursor(toProtoService.cursor(
                                                        new io.quarkus.panache.common.Page(0, 10)))
                                                .build()))
                                .collect()
                                .asList()),
                Status.Code.INVALID_ARGUMENT,
                "missing_header");
    }

    private @Nonnull Uni<Void> createSubscribedPosts(@Nonnull app.fyreplace.data.entities.User subscriber, int count) {
        Uni<Void> uni = Uni.createFrom().voidItem();
        for (var i = 0; i < count; i++) {
            uni = uni.chain(() -> createPublishedPost(OTHER_USER)
                    .flatMap(postId -> postService(token(subscriber))
                            .updateSubscription(app.fyreplace.protos.Subscription.newBuilder()
                                    .setId(toProtoService.byteString(postId))
                                    .setSubscribed(true)
                                    .build())
                            .replaceWithVoid()));
        }
        return uni;
    }

    private @Nonnull Uni<java.util.UUID> createPublishedPost(@Nonnull String username) {
        return user(username)
                .flatMap(u -> postService(token(u))
                        .create(com.google.protobuf.Empty.getDefaultInstance())
                        .map(id -> Tuple2.of(u, uuid(id))))
                .flatMap(pair -> addChapter(pair.getItem2(), "chapter").replaceWith(pair))
                .flatMap(pair -> postService(token(pair.getItem1()))
                        .publish(app.fyreplace.protos.Publication.newBuilder()
                                .setId(toProtoService.byteString(pair.getItem2()))
                                .build())
                        .replaceWith(pair.getItem2()));
    }
}
