package app.fyreplace.grpc.endpoints.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.Post;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Publication;
import app.fyreplace.protos.Subscription;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class UpdateSubscriptionTests extends PostTestsBase {
    @Test
    @RunOnVertxContext
    public void testSubscribe(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(ACTIVE_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .updateSubscription(subscription(pair.getItem2().getId(), true))
                                .replaceWith(pair))
                        .flatMap(pair -> session(() -> subscriptionRepository.findByUserIdAndPostIdOrNull(
                                pair.getItem1().getId(), pair.getItem2().getId()))),
                Assertions::assertNotNull);
    }

    @Test
    @RunOnVertxContext
    public void testSubscribeTwice(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> joinAsTuple(user(ACTIVE_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .updateSubscription(subscription(pair.getItem2().getId(), true))
                                .replaceWith(Empty.getDefaultInstance())
                                .chain(() -> postService(token(pair.getItem1()))
                                        .updateSubscription(
                                                subscription(pair.getItem2().getId(), true)))),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testUnsubscribe(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(ACTIVE_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .updateSubscription(subscription(pair.getItem2().getId(), true))
                                .replaceWith(pair))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .updateSubscription(subscription(pair.getItem2().getId(), false))
                                .replaceWith(pair))
                        .flatMap(pair -> session(() -> subscriptionRepository.findByUserIdAndPostIdOrNull(
                                pair.getItem1().getId(), pair.getItem2().getId()))),
                Assertions::assertNull);
    }

    @Test
    @RunOnVertxContext
    public void testSubscribeToOwnDraft(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .create(Empty.getDefaultInstance())
                                .flatMap(id ->
                                        postService(token(active)).updateSubscription(subscription(uuid(id), true)))),
                Status.Code.PERMISSION_DENIED,
                "post_not_published");
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active ->
                                postService(token(active)).updateSubscription(subscription(UUID.randomUUID(), true))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postService().updateSubscription(subscription(UUID.randomUUID(), true)),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testOtherDraftNotFound(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), postOf(OTHER_USER, false, 0))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .updateSubscription(subscription(pair.getItem2().getId(), true))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testDeletedPostNotFound(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> createPublishedPost(OTHER_USER)
                        .flatMap(postId -> user(OTHER_USER)
                                .flatMap(other -> postService(token(other))
                                        .delete(toProtoService.id(postId))
                                        .replaceWith(postId)))
                        .flatMap(postId -> user(ACTIVE_USER)
                                .flatMap(active ->
                                        postService(token(active)).updateSubscription(subscription(postId, true)))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testSubscribeNoComments(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createPublishedPost(OTHER_USER)
                        .flatMap(postId -> user(ACTIVE_USER)
                                .flatMap(active -> postService(token(active))
                                        .updateSubscription(subscription(postId, true))
                                        .replaceWith(Tuple2.of(active.getId(), postId))))
                        .flatMap(pair -> session(() ->
                                subscriptionRepository.findByUserIdAndPostIdOrNull(pair.getItem1(), pair.getItem2()))),
                subscription -> {
                    assertNotNull(subscription);
                    assertNull(subscription.getLastCommentSeen());
                });
    }

    @Test
    @RunOnVertxContext
    public void testResubscribeKeepsOriginalSeen(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createPublishedPost(OTHER_USER)
                        .flatMap(postId ->
                                addComment(postId, OTHER_USER).map(firstCommentId -> Tuple2.of(postId, firstCommentId)))
                        .flatMap(pair -> user(ACTIVE_USER)
                                .flatMap(active -> postService(token(active))
                                        .updateSubscription(subscription(pair.getItem1(), true))
                                        .replaceWith(Tuple2.of(active, pair))))
                        .flatMap(pair -> addComment(pair.getItem2().getItem1(), OTHER_USER)
                                .replaceWith(pair))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .updateSubscription(subscription(pair.getItem2().getItem1(), true))
                                .replaceWith(pair))
                        .flatMap(pair -> session(() -> Uni.combine()
                                .all()
                                .unis(
                                        subscriptionRepository.findByUserIdAndPostIdOrNull(
                                                pair.getItem1().getId(),
                                                pair.getItem2().getItem1()),
                                        subscriptionRepository.count(
                                                "user.id = ?1 AND post.id = ?2",
                                                pair.getItem1().getId(),
                                                pair.getItem2().getItem1()))
                                .asTuple()
                                .map(result -> Tuple2.of(result, pair.getItem2().getItem2())))),
                pair -> {
                    assertEquals(1, pair.getItem1().getItem2());
                    assertNotNull(pair.getItem1().getItem1());
                    assertEquals(
                            pair.getItem2(),
                            pair.getItem1().getItem1().getLastCommentSeen().getId());
                });
    }

    private @Nonnull Uni<UUID> createPublishedPost(@Nonnull String username) {
        return user(username)
                .flatMap(u ->
                        postService(token(u)).create(Empty.getDefaultInstance()).map(id -> Tuple2.of(u, uuid(id))))
                .flatMap(pair -> addChapter(pair.getItem2(), "chapter").replaceWith(pair))
                .flatMap(pair -> postService(token(pair.getItem1()))
                        .publish(Publication.newBuilder()
                                .setId(toProtoService.byteString(pair.getItem2()))
                                .build())
                        .replaceWith(pair.getItem2()));
    }

    private @Nonnull Uni<UUID> addComment(@Nonnull UUID postId, @Nonnull String author) {
        return user(author)
                .flatMap(u -> transaction(() -> {
                    final var comment = new Comment();
                    comment.setAuthor(u);
                    final var post = new Post();
                    post.setId(postId);
                    comment.setPost(post);
                    comment.setText("text");
                    return commentRepository.persist(comment).map(Comment::getId);
                }));
    }

    private @Nonnull Subscription subscription(@Nonnull UUID postId, boolean subscribed) {
        return Subscription.newBuilder()
                .setId(toProtoService.byteString(postId))
                .setSubscribed(subscribed)
                .build();
    }
}
