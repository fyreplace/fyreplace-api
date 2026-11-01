package app.fyreplace.grpc.endpoints.comment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Header;
import app.fyreplace.protos.Page;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class ListTests extends CommentTestsBase {
    @Test
    @RunOnVertxContext
    public void testListAnonymous(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> postOf(ACTIVE_USER, true, 1)
                        .flatMap(post -> commentService()
                                .list(commentRequest(post.getId(), 10, 0))
                                .collect()
                                .asList()
                                .map(bundles -> Tuple2.of(post, bundles))),
                pair -> {
                    assertEquals(1, pair.getItem2().size());
                    final var comments = pair.getItem2().getFirst().getCommentsList();
                    assertEquals(3, comments.size());
                    assertEquals("Howdy from user_1!", comments.get(0).getText());
                    assertEquals("user_1", comments.get(0).getAuthor().getUsername());
                    assertEquals(0, comments.get(0).getPosition());
                    assertEquals("Howdy also from user_2!", comments.get(1).getText());
                    assertEquals("user_2", comments.get(1).getAuthor().getUsername());
                    assertEquals(1, comments.get(1).getPosition());
                    assertTrue(comments.get(2).getIsDeleted());
                    assertEquals("", comments.get(2).getText());
                    assertEquals(2, comments.get(2).getPosition());
                });
    }

    @Test
    @RunOnVertxContext
    public void testOffsetPagination(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> postOf(ACTIVE_USER, true, 1)
                        .flatMap(post -> commentService()
                                .list(Multi.createFrom()
                                        .items(
                                                Page.newBuilder()
                                                        .setHeader(Header.newBuilder()
                                                                .setForward(true)
                                                                .setSize(2)
                                                                .setContextId(toProtoService.byteString(post.getId())))
                                                        .build(),
                                                Page.newBuilder().setOffset(0).build(),
                                                Page.newBuilder().setOffset(2).build()))
                                .collect()
                                .asList()
                                .map(bundles -> Tuple2.of(post, bundles))),
                pair -> {
                    assertEquals(2, pair.getItem2().size());
                    final var first = pair.getItem2().get(0);
                    assertEquals(3, first.getCount());
                    assertEquals(2, first.getCommentsCount());
                    assertEquals(0, first.getComments(0).getPosition());
                    assertEquals(1, first.getComments(1).getPosition());
                    final var second = pair.getItem2().get(1);
                    assertEquals(3, second.getCount());
                    assertEquals(1, second.getCommentsCount());
                    assertEquals(2, second.getComments(0).getPosition());
                });
    }

    @Test
    @RunOnVertxContext
    public void testInvalidContextId(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> commentService()
                        .list(Multi.createFrom()
                                .items(
                                        Page.newBuilder()
                                                .setHeader(Header.newBuilder()
                                                        .setForward(true)
                                                        .setSize(10)
                                                        .setContextId(ByteString.copyFromUtf8("invalid")))
                                                .build(),
                                        Page.newBuilder().setOffset(0).build()))
                        .collect()
                        .asList(),
                Status.Code.INVALID_ARGUMENT,
                "invalid_uuid");
    }

    @Test
    @RunOnVertxContext
    public void testPagination(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> postOf(ACTIVE_USER, true, 1)
                        .flatMap(post -> commentService()
                                .list(commentRequest(post.getId(), 2, 0, 1))
                                .collect()
                                .asList()),
                bundles -> {
                    assertEquals(2, bundles.size());
                    final var first = bundles.get(0);
                    assertEquals(3, first.getCount());
                    assertEquals(2, first.getCommentsCount());
                    assertEquals(0, first.getComments(0).getPosition());
                    final var second = bundles.get(1);
                    assertEquals(3, second.getCount());
                    assertEquals(1, second.getCommentsCount());
                    assertEquals(2, second.getComments(0).getPosition());
                });
    }

    @Test
    @RunOnVertxContext
    public void testReverse(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> postOf(ACTIVE_USER, true, 1)
                        .flatMap(post -> Uni.combine()
                                .all()
                                .unis(
                                        commentService()
                                                .list(commentRequest(post.getId(), true, 10, 0))
                                                .collect()
                                                .asList(),
                                        commentService()
                                                .list(commentRequest(post.getId(), false, 10, 0))
                                                .collect()
                                                .asList())
                                .asTuple()),
                tuple -> {
                    final var forward = tuple.getItem1().getFirst().getCommentsList();
                    final var backward = tuple.getItem2().getFirst().getCommentsList();
                    assertEquals(3, forward.size());
                    assertEquals(3, backward.size());
                    assertEquals(forward.get(0).getId(), backward.get(2).getId());
                    assertEquals(forward.get(2).getId(), backward.get(0).getId());
                });
    }

    @Test
    @RunOnVertxContext
    public void testOutOfBounds(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> postOf(ACTIVE_USER, true, 1)
                        .flatMap(post -> commentService()
                                .list(commentRequest(post.getId(), 10, 5))
                                .collect()
                                .asList()),
                bundles -> {
                    assertEquals(1, bundles.size());
                    assertEquals(0, bundles.getFirst().getCommentsCount());
                });
    }

    @Test
    @RunOnVertxContext
    public void testInvalidSize(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postOf(ACTIVE_USER, true, 1)
                        .flatMap(post -> commentService()
                                .list(Multi.createFrom()
                                        .items(Page.newBuilder()
                                                .setHeader(Header.newBuilder()
                                                        .setForward(true)
                                                        .setSize(0)
                                                        .setContextId(toProtoService.byteString(post.getId())))
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
                () -> postOf(ACTIVE_USER, true, 1)
                        .flatMap(post -> commentService()
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

    @Test
    @RunOnVertxContext
    public void testBannedAuthor(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> postOf(ACTIVE_USER, true, 1)
                        .flatMap(post -> user(TARGET_USER)
                                .flatMap(target -> session(
                                                () -> userRepository.updateBanByUserId(target.getId(), 36500))
                                        .chain(() ->
                                                session(() -> userRepository.updateBanByUserId(target.getId(), 36500)))
                                        .replaceWith(post)))
                        .flatMap(post -> commentService()
                                .list(commentRequest(post.getId(), 10, 0))
                                .collect()
                                .asList()
                                .map(bundles -> Tuple2.of(post, bundles)))
                        .call(pair -> session(() -> userRepository.resetBanByUsername(TARGET_USER))
                                .replaceWith(pair)),
                pair -> {
                    final var comments = pair.getItem2().getFirst().getCommentsList();
                    assertEquals(3, comments.size());
                    final var bannedAuthor = comments.get(1).getAuthor();
                    assertTrue(bannedAuthor.getIsBanned());
                    assertEquals("", bannedAuthor.getUsername());
                });
    }

    @Test
    @RunOnVertxContext
    public void testUnknownPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> commentService()
                        .list(commentRequest(UUID.randomUUID(), 10, 0))
                        .collect()
                        .asList(),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testDraftPostAsAuthor(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(OTHER_USER), postOf(OTHER_USER, false, 0))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .list(commentRequest(pair.getItem2().getId(), 10, 0))
                                .collect()
                                .asList()),
                Status.Code.PERMISSION_DENIED,
                "post_not_published");
    }

    @Test
    @RunOnVertxContext
    public void testDraftPostAnonymous(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postOf(OTHER_USER, false, 0)
                        .flatMap(post -> commentService()
                                .list(commentRequest(post.getId(), 10, 0))
                                .collect()
                                .asList()),
                Status.Code.NOT_FOUND,
                null);
    }
}
