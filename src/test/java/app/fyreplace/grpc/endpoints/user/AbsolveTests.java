package app.fyreplace.grpc.endpoints.user;

import app.fyreplace.data.entities.User;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class AbsolveTests extends UserTestsBase {
    @Test
    @RunOnVertxContext
    public void testCitizenAbsolvesCitizen(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> absolve(ACTIVE_USER, OTHER_USER),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testStaffAbsolvesCitizen(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.STAFF).chain(() -> absolve(ACTIVE_USER, OTHER_USER)),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testStaffAbsolvesStaff(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(ACTIVE_USER, User.Rank.STAFF)
                        .chain(() -> withRank(OTHER_USER, User.Rank.STAFF))
                        .chain(() -> absolve(ACTIVE_USER, OTHER_USER)),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testSuperuserAbsolvesCitizen(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.ADMIN).chain(() -> absolve(ACTIVE_USER, OTHER_USER)),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testSuperuserAbsolvesStaff(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.ADMIN)
                        .chain(() -> withRank(OTHER_USER, User.Rank.STAFF))
                        .chain(() -> absolve(ACTIVE_USER, OTHER_USER)),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testSelf(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), user(ACTIVE_USER))
                        .flatMap(pair -> absolve(
                                pair.getItem1().getUsername(), pair.getItem2().getUsername())),
                Status.Code.PERMISSION_DENIED,
                "user_is_caller");
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active)).absolve(toProtoService.id(UUID.randomUUID()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService().absolve(toProtoService.id(UUID.randomUUID())),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testCitizenAbsolvesStaff(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(OTHER_USER, User.Rank.STAFF).chain(() -> absolve(ACTIVE_USER, OTHER_USER)),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testStaffAbsolvesSuperuser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(ACTIVE_USER, User.Rank.STAFF)
                        .chain(() -> withRank(OTHER_USER, User.Rank.ADMIN))
                        .chain(() -> absolve(ACTIVE_USER, OTHER_USER)),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testSuperuserAbsolvesSuperuser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(ACTIVE_USER, User.Rank.ADMIN)
                        .chain(() -> withRank(OTHER_USER, User.Rank.ADMIN))
                        .chain(() -> absolve(ACTIVE_USER, OTHER_USER)),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testAbsolveWithoutReports(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.STAFF).chain(() -> absolve(ACTIVE_USER, OTHER_USER)),
                Empty.getDefaultInstance());
    }

    private @Nonnull Uni<Empty> absolve(@Nonnull String callerUsername, @Nonnull String targetUsername) {
        return joinAsTuple(user(callerUsername), user(targetUsername))
                .flatMap(pair -> userService(token(pair.getItem1()))
                        .absolve(toProtoService.id(pair.getItem2().getId())));
    }

    private @Nonnull Uni<Void> withRank(@Nonnull String username, @Nonnull User.Rank rank) {
        return user(username)
                .flatMap(u -> transaction(() -> userRepository.updateRankByUserId(u.getId(), rank)))
                .replaceWithVoid();
    }
}
