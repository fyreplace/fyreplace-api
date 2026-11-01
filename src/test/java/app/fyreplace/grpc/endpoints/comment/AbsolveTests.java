package app.fyreplace.grpc.endpoints.comment;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.CommentCreation;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class AbsolveTests extends CommentTestsBase {
    @Test
    @RunOnVertxContext
    public void testStaffAbsolvesReport(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(ACTIVE_USER), user(STAFF_USER))
                        .flatMap(pair -> postOf(ACTIVE_USER, true, 1)
                                .flatMap(post -> commentOf(post, 1)
                                        .flatMap(comment -> commentService(token(pair.getItem1()))
                                                .report(toProtoService.id(comment.getId()))
                                                .replaceWith(comment))
                                        .flatMap(comment -> commentService(token(pair.getItem2()))
                                                .absolve(toProtoService.id(comment.getId()))
                                                .replaceWith(comment))
                                        .flatMap(comment ->
                                                session(() -> reportRepository.existsByIssuerIdAndTargetCommentId(
                                                        pair.getItem1().getId(), comment.getId()))))),
                Assertions::assertFalse);
    }

    @Test
    @RunOnVertxContext
    public void testCitizenCannotAbsolve(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentOf(pair.getItem2(), 1)
                                .flatMap(comment -> commentService(token(pair.getItem1()))
                                        .absolve(toProtoService.id(comment.getId())))),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testStaffCannotAbsolveOwnComment(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(STAFF_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentService(token(pair.getItem1()))
                                .create(CommentCreation.newBuilder()
                                        .setPostId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setText("staff comment")
                                        .build())
                                .flatMap(id ->
                                        commentService(token(pair.getItem1())).absolve(toProtoService.id(uuid(id))))),
                Status.Code.PERMISSION_DENIED,
                "caller_owns_comment");
    }

    @Test
    @RunOnVertxContext
    public void testDeletedComment(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(STAFF_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentOf(pair.getItem2(), 2)
                                .flatMap(comment -> commentService(token(pair.getItem1()))
                                        .absolve(toProtoService.id(comment.getId())))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(STAFF_USER)
                        .flatMap(staff -> commentService(token(staff)).absolve(toProtoService.id(UUID.randomUUID()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> commentService().absolve(toProtoService.id(UUID.randomUUID())),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
