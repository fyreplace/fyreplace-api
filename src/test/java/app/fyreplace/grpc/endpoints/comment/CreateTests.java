package app.fyreplace.grpc.endpoints.comment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.fyreplace.data.entities.Block;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.CommentCreation;
import app.fyreplace.protos.Publication;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.tuples.Tuple2;
import io.smallrye.mutiny.tuples.Tuple3;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class CreateTests extends CommentTestsBase {
    @Test
    @RunOnVertxContext
    public void testCreate(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .create(CommentCreation.newBuilder()
                                        .setPostId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setText("a fresh comment")
                                        .build())
                                .map(id -> Tuple3.of(pair.getItem1(), pair.getItem2(), id)))
                        .flatMap(triple -> session(() -> commentRepository.findById(uuid(triple.getItem3())))
                                .map(comment -> Tuple3.of(triple.getItem1(), triple.getItem2(), comment))),
                triple -> {
                    final var comment = triple.getItem3();
                    assertEquals(triple.getItem1().getId(), comment.getAuthor().getId());
                    assertEquals(triple.getItem2().getId(), comment.getPost().getId());
                    assertEquals("a fresh comment", comment.getText());
                    assertFalse(comment.isDeleted());
                });
    }

    @Test
    @RunOnVertxContext
    public void testEmptyText(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .create(CommentCreation.newBuilder()
                                        .setPostId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setText("")
                                        .build())),
                Status.Code.INVALID_ARGUMENT,
                "comment_empty");
    }

    @Test
    @RunOnVertxContext
    public void testTooLongText(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .create(CommentCreation.newBuilder()
                                        .setPostId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setText("x".repeat(1501))
                                        .build())),
                Status.Code.INVALID_ARGUMENT,
                "invalid_text");
    }

    @Test
    @RunOnVertxContext
    public void testDraftPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(OTHER_USER), postOf(OTHER_USER, false, 0))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .create(CommentCreation.newBuilder()
                                        .setPostId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setText("hello")
                                        .build())),
                Status.Code.PERMISSION_DENIED,
                "post_not_published");
    }

    @Test
    @RunOnVertxContext
    public void testNonExistentPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(OTHER_USER)
                        .flatMap(u -> commentService(token(u))
                                .create(CommentCreation.newBuilder()
                                        .setPostId(toProtoService.byteString(UUID.randomUUID()))
                                        .setText("hello")
                                        .build())),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testBlockedByAuthor(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER))
                        .flatMap(pair -> transaction(() -> {
                                    final var block = new Block();
                                    block.setIssuer(pair.getItem1());
                                    block.setTarget(pair.getItem2());
                                    return blockRepository.persist(block).replaceWithVoid();
                                })
                                .chain(() -> postOf(ACTIVE_USER, true, 1))
                                .flatMap(post -> commentService(token(pair.getItem2()))
                                        .create(CommentCreation.newBuilder()
                                                .setPostId(toProtoService.byteString(post.getId()))
                                                .setText("hello")
                                                .build()))
                                .onFailure()
                                .call(_ -> transaction(() -> blockRepository
                                        .deleteByIssuerIdAndTargetId(
                                                pair.getItem1().getId(),
                                                pair.getItem2().getId())
                                        .replaceWithVoid()))),
                Status.Code.PERMISSION_DENIED,
                "caller_blocked");
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postOf(ACTIVE_USER, true, 1)
                        .flatMap(post -> commentService()
                                .create(CommentCreation.newBuilder()
                                        .setPostId(toProtoService.byteString(post.getId()))
                                        .setText("hello")
                                        .build())),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testOtherDraftNotFound(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), postOf(OTHER_USER, false, 0))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .create(CommentCreation.newBuilder()
                                        .setPostId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setText("hello")
                                        .build())),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testDeletedPostNotFound(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(OTHER_USER)
                        .flatMap(other -> postService(token(other))
                                .create(Empty.getDefaultInstance())
                                .map(id -> Tuple2.of(other, uuid(id))))
                        .flatMap(pair -> addChapter(pair.getItem2(), "hello").replaceWith(pair))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .publish(Publication.newBuilder()
                                        .setId(toProtoService.byteString(pair.getItem2()))
                                        .build())
                                .replaceWith(pair))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .delete(toProtoService.id(pair.getItem2()))
                                .replaceWith(pair))
                        .flatMap(pair -> user(ACTIVE_USER)
                                .flatMap(active -> commentService(token(active))
                                        .create(CommentCreation.newBuilder()
                                                .setPostId(toProtoService.byteString(pair.getItem2()))
                                                .setText("hello")
                                                .build()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testCreateAutoSubscribesAuthor(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .create(CommentCreation.newBuilder()
                                        .setPostId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setText("a fresh comment")
                                        .build())
                                .map(id -> Tuple3.of(pair.getItem1(), pair.getItem2(), uuid(id))))
                        .flatMap(triple -> session(() -> subscriptionRepository.findByUserIdAndPostIdOrNull(
                                        triple.getItem1().getId(),
                                        triple.getItem2().getId()))
                                .map(subscription -> Tuple3.of(triple.getItem1(), triple.getItem3(), subscription))),
                triple -> {
                    final var subscription = triple.getItem3();
                    assertNotNull(subscription);
                    assertEquals(
                            triple.getItem2(), subscription.getLastCommentSeen().getId());
                });
    }
}
