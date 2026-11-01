package app.fyreplace.grpc.endpoints.comment;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import com.google.protobuf.Empty;
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
public final class ReportTests extends CommentTestsBase {
    @Test
    @RunOnVertxContext
    public void testReport(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(ACTIVE_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentOf(pair.getItem2(), 1)
                                .flatMap(comment -> commentService(token(pair.getItem1()))
                                        .report(toProtoService.id(comment.getId()))
                                        .replaceWith(comment))
                                .flatMap(comment -> session(() -> reportRepository.existsByIssuerIdAndTargetCommentId(
                                        pair.getItem1().getId(), comment.getId())))),
                Assertions::assertTrue);
    }

    @Test
    @RunOnVertxContext
    public void testReportTwice(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> joinAsTuple(user(ACTIVE_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentOf(pair.getItem2(), 1)
                                .flatMap(comment -> commentService(token(pair.getItem1()))
                                        .report(toProtoService.id(comment.getId()))
                                        .replaceWith(Empty.getDefaultInstance())
                                        .chain(() -> commentService(token(pair.getItem1()))
                                                .report(toProtoService.id(comment.getId()))))),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testReportOwnComment(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentOf(pair.getItem2(), 0)
                                .flatMap(comment -> commentService(token(pair.getItem1()))
                                        .report(toProtoService.id(comment.getId())))),
                Status.Code.PERMISSION_DENIED,
                "invalid_comment");
    }

    @Test
    @RunOnVertxContext
    public void testReportDeletedComment(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> commentOf(pair.getItem2(), 2)
                                .flatMap(comment -> commentService(token(pair.getItem1()))
                                        .report(toProtoService.id(comment.getId())))),
                Status.Code.PERMISSION_DENIED,
                "comment_deleted");
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> commentService(token(active)).report(toProtoService.id(UUID.randomUUID()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> commentService().report(toProtoService.id(UUID.randomUUID())),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
