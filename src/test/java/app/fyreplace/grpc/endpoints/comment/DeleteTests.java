package app.fyreplace.grpc.endpoints.comment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.CommentCreation;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class DeleteTests extends CommentTestsBase {
    @Test
    @RunOnVertxContext
    public void testAuthorDeletes(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .create(CommentCreation.newBuilder()
                                        .setPostId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setText("to be deleted")
                                        .build())
                                .map(id -> Tuple2.of(pair.getItem1(), id)))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .delete(toProtoService.id(uuid(pair.getItem2())))
                                .replaceWith(pair))
                        .flatMap(pair -> session(() -> commentRepository.findById(uuid(pair.getItem2())))
                                .map(comment -> Tuple2.of(pair.getItem1(), comment))),
                pair -> {
                    assertTrue(pair.getItem2().isDeleted());
                    assertEquals("", pair.getItem2().getText());
                });
    }

    @Test
    @RunOnVertxContext
    public void testStaffDeletes(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(STAFF_USER), user(OTHER_USER))
                        .flatMap(pair -> postOf(ACTIVE_USER, true, 1)
                                .flatMap(post -> commentService(token(pair.getItem2()))
                                        .create(CommentCreation.newBuilder()
                                                .setPostId(toProtoService.byteString(post.getId()))
                                                .setText("staff will delete this")
                                                .build()))
                                .flatMap(id -> commentService(token(pair.getItem1()))
                                        .delete(toProtoService.id(uuid(id)))
                                        .replaceWith(id)))
                        .flatMap(id -> session(() -> commentRepository.findById(uuid(id)))),
                comment -> assertTrue(comment.isDeleted()));
    }

    @Test
    @RunOnVertxContext
    public void testOtherUserCannotDelete(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentOf(pair.getItem2(), 1)
                                .flatMap(comment -> commentService(token(pair.getItem1()))
                                        .delete(toProtoService.id(comment.getId())))),
                Status.Code.PERMISSION_DENIED,
                "invalid_comment_author");
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> commentService(token(active)).delete(toProtoService.id(UUID.randomUUID()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> commentService().delete(toProtoService.id(UUID.randomUUID())),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testAlreadyDeleted(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .create(CommentCreation.newBuilder()
                                        .setPostId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setText("to be deleted")
                                        .build())
                                .map(id -> Tuple2.of(pair.getItem1(), uuid(id))))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .delete(toProtoService.id(pair.getItem2()))
                                .replaceWith(pair))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .delete(toProtoService.id(pair.getItem2()))
                                .replaceWith(pair))
                        .flatMap(pair -> session(() -> commentRepository.findById(pair.getItem2()))),
                comment -> {
                    assertTrue(comment.isDeleted());
                    assertEquals("", comment.getText());
                });
    }
}
