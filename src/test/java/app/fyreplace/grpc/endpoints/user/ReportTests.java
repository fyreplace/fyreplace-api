package app.fyreplace.grpc.endpoints.user;

import app.fyreplace.data.entities.Report;
import app.fyreplace.data.entities.User;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class ReportTests extends UserTestsBase {
    @Inject
    Report.Repository reportRepository;

    @Test
    @RunOnVertxContext
    public void testCitizenReportsCitizen(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(() -> report(ACTIVE_USER, OTHER_USER), Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testCitizenReportsStaff(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(OTHER_USER, User.Rank.STAFF).chain(() -> report(ACTIVE_USER, OTHER_USER)),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testStaffReportsCitizen(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.STAFF).chain(() -> report(ACTIVE_USER, OTHER_USER)),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testStaffReportsStaff(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.STAFF)
                        .chain(() -> withRank(OTHER_USER, User.Rank.STAFF))
                        .chain(() -> report(ACTIVE_USER, OTHER_USER)),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testSuperuserReportsCitizen(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.ADMIN).chain(() -> report(ACTIVE_USER, OTHER_USER)),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testSelf(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), user(ACTIVE_USER))
                        .flatMap(pair -> report(
                                pair.getItem1().getUsername(), pair.getItem2().getUsername())),
                Status.Code.PERMISSION_DENIED,
                "user_is_caller");
    }

    @Test
    @RunOnVertxContext
    public void testNotActive(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(OTHER_USER)
                        .flatMap(other -> transaction(() -> userRepository.updateActiveById(other.getId(), false))
                                .replaceWith(other))
                        .chain(() -> report(ACTIVE_USER, OTHER_USER)),
                Status.Code.PERMISSION_DENIED,
                "user_not_active");
    }

    @Test
    @RunOnVertxContext
    public void testBanned(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(OTHER_USER)
                        .flatMap(other -> transaction(() -> userRepository.updateBanByUserId(other.getId(), 3))
                                .replaceWith(other))
                        .chain(() -> report(ACTIVE_USER, OTHER_USER)),
                Status.Code.PERMISSION_DENIED,
                "user_banned");
    }

    @Test
    @RunOnVertxContext
    public void testTwice(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> report(ACTIVE_USER, OTHER_USER)
                        .replaceWith(Empty.getDefaultInstance())
                        .chain(() -> report(ACTIVE_USER, OTHER_USER)),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active)).report(toProtoService.id(UUID.randomUUID()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService().report(toProtoService.id(UUID.randomUUID())),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testCitizenReportsSuperuser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(OTHER_USER, User.Rank.ADMIN).chain(() -> report(ACTIVE_USER, OTHER_USER)),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testStaffReportsSuperuser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(ACTIVE_USER, User.Rank.STAFF)
                        .chain(() -> withRank(OTHER_USER, User.Rank.ADMIN))
                        .chain(() -> report(ACTIVE_USER, OTHER_USER)),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testSuperuserReportsStaff(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.ADMIN)
                        .chain(() -> withRank(OTHER_USER, User.Rank.STAFF))
                        .chain(() -> report(ACTIVE_USER, OTHER_USER)),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testSuperuserReportsSuperuser(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.ADMIN)
                        .chain(() -> withRank(OTHER_USER, User.Rank.ADMIN))
                        .chain(() -> report(ACTIVE_USER, OTHER_USER)),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testDeleted(@Nonnull UniAsserter asserter) {
        assertFailure(asserter, () -> report(ACTIVE_USER, "deleted_user"), Status.Code.NOT_FOUND, null);
    }

    private @Nonnull io.smallrye.mutiny.Uni<Empty> report(
            @Nonnull String callerUsername, @Nonnull String targetUsername) {
        return joinAsTuple(user(callerUsername), user(targetUsername))
                .flatMap(pair -> userService(token(pair.getItem1()))
                        .report(toProtoService.id(pair.getItem2().getId())));
    }

    private @Nonnull io.smallrye.mutiny.Uni<Void> withRank(@Nonnull String username, @Nonnull User.Rank rank) {
        return user(username)
                .flatMap(u -> transaction(() -> userRepository.updateRankByUserId(u.getId(), rank)))
                .replaceWithVoid();
    }
}
