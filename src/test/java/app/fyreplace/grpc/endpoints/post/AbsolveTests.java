package app.fyreplace.grpc.endpoints.post;

import app.fyreplace.data.entities.User;
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
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class AbsolveTests extends PostTestsBase {
    @Test
    @RunOnVertxContext
    public void testStaffAbsolvesReport(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .report(toProtoService.id(pair.getItem2().getId()))
                                .replaceWith(pair))
                        .flatMap(pair -> user(STAFF_USER)
                                .flatMap(staff -> postService(token(staff))
                                        .absolve(toProtoService.id(
                                                pair.getItem2().getId()))
                                        .replaceWith(pair)))
                        .flatMap(pair -> session(() -> reportRepository.existsByIssuerIdAndTargetPostId(
                                pair.getItem1().getId(), pair.getItem2().getId()))),
                Assertions::assertFalse);
    }

    @Test
    @RunOnVertxContext
    public void testCitizenCannotAbsolve(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .absolve(toProtoService.id(pair.getItem2().getId()))),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testAbsolveOwnPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> transaction(
                                        () -> userRepository.updateRankByUserId(active.getId(), User.Rank.STAFF))
                                .replaceWith(active))
                        .flatMap(active -> postService(token(active))
                                .create(Empty.getDefaultInstance())
                                .flatMap(id -> postService(token(active)).absolve(toProtoService.id(uuid(id))))),
                Status.Code.PERMISSION_DENIED,
                "caller_owns_post");
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(STAFF_USER)
                        .flatMap(staff -> postService(token(staff)).absolve(toProtoService.id(UUID.randomUUID()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postService().absolve(toProtoService.id(UUID.randomUUID())),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testAbsolveWithoutReports(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .create(Empty.getDefaultInstance())
                                .map(id -> Tuple2.of(active, uuid(id))))
                        .flatMap(pair -> addChapter(pair.getItem2(), "hello").replaceWith(pair))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .publish(Publication.newBuilder()
                                        .setId(toProtoService.byteString(pair.getItem2()))
                                        .build())
                                .replaceWith(pair.getItem2()))
                        .flatMap(postId -> user(STAFF_USER)
                                .flatMap(staff -> postService(token(staff)).absolve(toProtoService.id(postId)))),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testAbsolveDraft(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(STAFF_USER), postOf(OTHER_USER, false, 0))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .absolve(toProtoService.id(pair.getItem2().getId()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testAbsolveDeleted(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .create(Empty.getDefaultInstance())
                                .map(id -> Tuple2.of(active, uuid(id))))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .delete(toProtoService.id(pair.getItem2()))
                                .replaceWith(pair.getItem2()))
                        .flatMap(postId -> user(STAFF_USER)
                                .flatMap(staff -> postService(token(staff)).absolve(toProtoService.id(postId)))),
                Status.Code.NOT_FOUND,
                null);
    }
}
