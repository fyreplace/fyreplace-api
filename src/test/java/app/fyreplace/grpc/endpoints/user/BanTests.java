package app.fyreplace.grpc.endpoints.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.data.entities.User;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.BanSentence;
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
public final class BanTests extends UserTestsBase {
    @Test
    @RunOnVertxContext
    public void testCitizenBansCitizen(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> ban(ACTIVE_USER, BAN_TARGET, 3),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testStaffBansCitizen(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.STAFF).chain(() -> ban(ACTIVE_USER, BAN_TARGET, 3)),
                Empty.getDefaultInstance());
        asserter.assertThat(() -> user(BAN_TARGET), target -> {
            assertTrue(target.isBanned());
            assertNotNull(target.getDateBanEnd());
        });
    }

    @Test
    @RunOnVertxContext
    public void testStaffBansStaff(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(ACTIVE_USER, User.Rank.STAFF)
                        .chain(() -> withRank(BAN_TARGET, User.Rank.STAFF))
                        .chain(() -> ban(ACTIVE_USER, BAN_TARGET, 3)),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testSuperuserBansCitizen(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.ADMIN).chain(() -> ban(ACTIVE_USER, BAN_TARGET, 3)),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testSelf(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(ACTIVE_USER, User.Rank.ADMIN).chain(() -> ban(ACTIVE_USER, ACTIVE_USER, 3)),
                Status.Code.PERMISSION_DENIED,
                "user_is_caller");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidDays(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(ACTIVE_USER, User.Rank.STAFF).chain(() -> ban(ACTIVE_USER, BAN_TARGET, 0)),
                Status.Code.INVALID_ARGUMENT,
                "invalid_days");
    }

    @Test
    @RunOnVertxContext
    public void testNegativeDays(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(ACTIVE_USER, User.Rank.STAFF).chain(() -> ban(ACTIVE_USER, BAN_TARGET, -1)),
                Status.Code.INVALID_ARGUMENT,
                "invalid_days");
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(ACTIVE_USER, User.Rank.STAFF)
                        .chain(() -> user(ACTIVE_USER)
                                .flatMap(active -> userService(token(active))
                                        .ban(BanSentence.newBuilder()
                                                .setId(toProtoService.byteString(UUID.randomUUID()))
                                                .setDays(3)
                                                .build()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMailSent(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.STAFF).chain(() -> ban(ACTIVE_USER, BAN_TARGET, 3)),
                Empty.getDefaultInstance());
        asserter.assertThat(
                () -> Uni.createFrom().item(mailbox.getMailMessagesSentTo("ban_target@example.org")),
                mails -> assertEquals(1, mails.size()));
    }

    @Test
    @RunOnVertxContext
    public void testCitizenBansStaff(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(BAN_TARGET, User.Rank.STAFF).chain(() -> ban(ACTIVE_USER, BAN_TARGET, 3)),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testCitizenBansSuperuser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(BAN_TARGET, User.Rank.ADMIN).chain(() -> ban(ACTIVE_USER, BAN_TARGET, 3)),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testStaffBansSuperuser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(ACTIVE_USER, User.Rank.STAFF)
                        .chain(() -> withRank(BAN_TARGET, User.Rank.ADMIN))
                        .chain(() -> ban(ACTIVE_USER, BAN_TARGET, 3)),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testSuperuserBansStaff(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(ACTIVE_USER, User.Rank.ADMIN)
                        .chain(() -> withRank(BAN_TARGET, User.Rank.STAFF))
                        .chain(() -> ban(ACTIVE_USER, BAN_TARGET, 3)),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testSuperuserBansSuperuser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(ACTIVE_USER, User.Rank.ADMIN)
                        .chain(() -> withRank(BAN_TARGET, User.Rank.ADMIN))
                        .chain(() -> ban(ACTIVE_USER, BAN_TARGET, 3)),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testDeletedTarget(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> withRank(ACTIVE_USER, User.Rank.STAFF).chain(() -> ban(ACTIVE_USER, "deleted_user", 3)),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService()
                        .ban(BanSentence.newBuilder()
                                .setId(toProtoService.byteString(UUID.randomUUID()))
                                .setDays(3)
                                .build()),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    private @Nonnull Uni<Empty> ban(@Nonnull String callerUsername, @Nonnull String targetUsername, int days) {
        return joinAsTuple(user(callerUsername), user(targetUsername))
                .flatMap(pair -> userService(token(pair.getItem1()))
                        .ban(BanSentence.newBuilder()
                                .setId(toProtoService.byteString(pair.getItem2().getId()))
                                .setDays(days)
                                .build()));
    }

    private @Nonnull Uni<Void> withRank(@Nonnull String username, @Nonnull User.Rank rank) {
        return user(username)
                .flatMap(u -> transaction(() -> userRepository.updateRankByUserId(u.getId(), rank)))
                .replaceWithVoid();
    }
}
