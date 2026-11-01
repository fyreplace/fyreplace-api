package app.fyreplace.grpc.endpoints.comment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class AcknowledgeTests extends CommentTestsBase {
    @Test
    @RunOnVertxContext
    public void testAcknowledgeMovesLastSeenForward(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(u -> postOf(ACTIVE_USER, true, 1)
                                .flatMap(post -> commentOf(post, 1)
                                        .flatMap(comment -> commentService(token(u))
                                                .acknowledge(toProtoService.id(comment.getId()))
                                                .replaceWith(comment))
                                        .flatMap(comment -> session(
                                                        () -> subscriptionRepository.findByUserIdAndPostIdOrNull(
                                                                u.getId(), post.getId()))
                                                .map(subscription -> Tuple2.of(
                                                        subscription
                                                                .getLastCommentSeen()
                                                                .getId(),
                                                        comment.getId()))))),
                pair -> assertEquals(pair.getItem2(), pair.getItem1()));
    }

    @Test
    @RunOnVertxContext
    public void testAcknowledgeOlderCommentDoesNotMoveBack(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(u -> postOf(ACTIVE_USER, true, 1)
                                .flatMap(post -> commentOf(post, 2)
                                        .flatMap(latest -> commentService(token(u))
                                                .acknowledge(toProtoService.id(latest.getId()))
                                                .replaceWith(latest))
                                        .flatMap(latest -> commentOf(post, 0)
                                                .flatMap(oldest -> commentService(token(u))
                                                        .acknowledge(toProtoService.id(oldest.getId()))
                                                        .replaceWith(latest)))
                                        .flatMap(latest -> session(
                                                        () -> subscriptionRepository.findByUserIdAndPostIdOrNull(
                                                                u.getId(), post.getId()))
                                                .map(subscription -> Tuple2.of(
                                                        subscription
                                                                .getLastCommentSeen()
                                                                .getId(),
                                                        latest.getId()))))),
                pair -> assertEquals(pair.getItem2(), pair.getItem1()));
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(OTHER_USER)
                        .flatMap(u -> commentService(token(u)).acknowledge(toProtoService.id(UUID.randomUUID()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testNotSubscribed(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(other -> postService(token(other))
                                .create(com.google.protobuf.Empty.getDefaultInstance())
                                .map(id -> Tuple2.of(other, uuid(id))))
                        .flatMap(pair -> addChapter(pair.getItem2(), "hello").replaceWith(pair))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .publish(app.fyreplace.protos.Publication.newBuilder()
                                        .setId(toProtoService.byteString(pair.getItem2()))
                                        .build())
                                .replaceWith(pair))
                        .flatMap(pair -> transaction(() -> {
                            final var comment = new app.fyreplace.data.entities.Comment();
                            comment.setAuthor(pair.getItem1());
                            final var post = new app.fyreplace.data.entities.Post();
                            post.setId(pair.getItem2());
                            comment.setPost(post);
                            comment.setText("hi");
                            return commentRepository.persist(comment).replaceWith(pair);
                        }))
                        .flatMap(pair -> session(() -> commentRepository
                                        .findByPostId(pair.getItem2(), true)
                                        .list())
                                .flatMap(comments -> user(ACTIVE_USER)
                                        .flatMap(active -> commentService(token(active))
                                                .acknowledge(toProtoService.id(
                                                        comments.getFirst().getId()))
                                                .replaceWith(Tuple2.of(active.getId(), pair.getItem2())))))
                        .flatMap(pair -> session(() ->
                                subscriptionRepository.findByUserIdAndPostIdOrNull(pair.getItem1(), pair.getItem2()))),
                Assertions::assertNull);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> commentService().acknowledge(toProtoService.id(UUID.randomUUID())),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
