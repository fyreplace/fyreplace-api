package app.fyreplace.grpc.endpoints.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.Post;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Publication;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class RetrieveTests extends PostTestsBase {
    @Test
    @RunOnVertxContext
    public void testPublishedPost(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .retrieve(toProtoService.id(pair.getItem2().getId()))
                                .map(post -> Tuple2.of(pair.getItem2(), post))),
                pair -> {
                    final var post = pair.getItem2();
                    assertEquals(toProtoService.byteString(pair.getItem1().getId()), post.getId());
                    assertFalse(post.getIsPreview());
                    assertEquals(2, post.getChapterCount());
                    assertEquals(2, post.getChaptersCount());
                    assertEquals(ACTIVE_USER, post.getAuthor().getUsername());
                    assertTrue(post.getIsSubscribed());
                    assertEquals(1, post.getCommentsRead());
                    assertEquals(3, post.getCommentCount());
                    assertEquals(1, post.getVoteCount());
                });
    }

    @Test
    @RunOnVertxContext
    public void testAnonymousPostHidesAuthor(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 0))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .retrieve(toProtoService.id(pair.getItem2().getId()))),
                post -> {
                    assertTrue(post.getIsAnonymous());
                    assertFalse(post.hasAuthor());
                });
    }

    @Test
    @RunOnVertxContext
    public void testAnonymousPostShowsAuthorToAuthor(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(ACTIVE_USER), postOf(ACTIVE_USER, true, 0))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .retrieve(toProtoService.id(pair.getItem2().getId()))),
                post -> {
                    assertTrue(post.getIsAnonymous());
                    assertEquals(ACTIVE_USER, post.getAuthor().getUsername());
                });
    }

    @Test
    @RunOnVertxContext
    public void testOwnDraft(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(OTHER_USER, false, 0))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .retrieve(toProtoService.id(pair.getItem2().getId()))
                                .map(post -> Tuple2.of(pair.getItem2(), post))),
                pair -> {
                    assertEquals(
                            toProtoService.byteString(pair.getItem1().getId()),
                            pair.getItem2().getId());
                    assertEquals(0, pair.getItem2().getChapterCount());
                    assertFalse(pair.getItem2().getIsPreview());
                    assertFalse(pair.getItem2().getIsSubscribed());
                    assertEquals(0, pair.getItem2().getCommentsRead());
                });
    }

    @Test
    @RunOnVertxContext
    public void testOtherUsersDraft(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), postOf(OTHER_USER, false, 0))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .retrieve(toProtoService.id(pair.getItem2().getId()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testDeletedPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), anyPostOf(OTHER_USER, 0))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .retrieve(toProtoService.id(pair.getItem2().getId()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testCommentIdReturnsItsPost(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postOf(ACTIVE_USER, true, 1)
                                .flatMap(post -> commentOf(post, 0)
                                        .flatMap(comment -> postService(token(active))
                                                .retrieve(toProtoService.id(comment.getId()))
                                                .map(result -> Tuple2.of(post, result))))),
                pair -> assertEquals(
                        toProtoService.byteString(pair.getItem1().getId()),
                        pair.getItem2().getId()));
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active)).retrieve(toProtoService.id(UUID.randomUUID()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postOf(ACTIVE_USER, true, 1)
                        .flatMap(post -> postService().retrieve(toProtoService.id(post.getId()))),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testCommentOnDeletedPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .create(Empty.getDefaultInstance())
                                .map(id -> Tuple2.of(active, uuid(id))))
                        .flatMap(pair -> addChapter(pair.getItem2(), "hello").replaceWith(pair))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .publish(Publication.newBuilder()
                                        .setId(toProtoService.byteString(pair.getItem2()))
                                        .build())
                                .replaceWith(pair))
                        .flatMap(pair -> transaction(() -> {
                            final var comment = new Comment();
                            comment.setAuthor(pair.getItem1());
                            final var post = new Post();
                            post.setId(pair.getItem2());
                            comment.setPost(post);
                            comment.setText("hi");
                            return commentRepository
                                    .persist(comment)
                                    .map(Comment::getId)
                                    .map(commentId -> Tuple2.of(pair, commentId));
                        }))
                        .flatMap(pair -> postService(token(pair.getItem1().getItem1()))
                                .delete(toProtoService.id(pair.getItem1().getItem2()))
                                .replaceWith(pair))
                        .flatMap(pair -> postService(token(pair.getItem1().getItem1()))
                                .retrieve(toProtoService.id(pair.getItem2()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testCommentOtherAnonymous(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(other -> postService(token(other))
                                .create(Empty.getDefaultInstance())
                                .map(id -> Tuple2.of(other, uuid(id))))
                        .flatMap(pair -> addChapter(pair.getItem2(), "hello").replaceWith(pair))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .publish(Publication.newBuilder()
                                        .setId(toProtoService.byteString(pair.getItem2()))
                                        .setAnonymous(true)
                                        .build())
                                .replaceWith(pair))
                        .flatMap(pair -> transaction(() -> {
                            final var comment = new Comment();
                            comment.setAuthor(pair.getItem1());
                            final var post = new Post();
                            post.setId(pair.getItem2());
                            comment.setPost(post);
                            comment.setText("hi");
                            return commentRepository.persist(comment).replaceWith(pair);
                        }))
                        .flatMap(pair -> session(() -> commentRepository
                                        .findByPostId(pair.getItem2(), true)
                                        .list())
                                .flatMap(comments -> user(TARGET_USER)
                                        .flatMap(caller -> postService(token(caller))
                                                .retrieve(toProtoService.id(
                                                        comments.getFirst().getId()))
                                                .map(post -> Tuple2.of(pair.getItem2(), post))))),
                pair -> {
                    assertEquals(
                            toProtoService.byteString(pair.getItem1()),
                            pair.getItem2().getId());
                    assertFalse(pair.getItem2().hasAuthor());
                });
    }

    @Test
    @RunOnVertxContext
    public void testDateLastSeenUpdated(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> session(() -> subscriptionRepository.findByUserIdAndPostIdOrNull(
                                        pair.getItem1().getId(), pair.getItem2().getId()))
                                .flatMap(subscription -> session(() -> subscriptionRepository.updateLastSeenById(
                                                subscription.getId(),
                                                OffsetDateTime.now().minusDays(1)))
                                        .replaceWith(pair)))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .retrieve(toProtoService.id(pair.getItem2().getId()))
                                .replaceWith(pair))
                        .flatMap(pair -> session(() -> subscriptionRepository.findByUserIdAndPostIdOrNull(
                                pair.getItem1().getId(), pair.getItem2().getId()))),
                subscription -> assertTrue(subscription
                        .getDateLastSeen()
                        .isAfter(OffsetDateTime.now().minusMinutes(1))));
    }
}
