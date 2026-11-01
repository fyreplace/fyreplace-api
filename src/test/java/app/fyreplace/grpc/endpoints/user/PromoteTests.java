package app.fyreplace.grpc.endpoints.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.fyreplace.data.entities.User;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Promotion;
import app.fyreplace.protos.Rank;
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
public final class PromoteTests extends UserTestsBase {
    @Test
    @RunOnVertxContext
    public void testCitizenToStaff(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(() -> promote(ADMIN_USER, OTHER_USER, Rank.RANK_STAFF), Empty.getDefaultInstance());
        asserter.assertThat(() -> user(OTHER_USER), target -> assertEquals(User.Rank.STAFF, target.getRank()));
    }

    @Test
    @RunOnVertxContext
    public void testStaffToSuperuser(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(OTHER_USER, User.Rank.STAFF)
                        .chain(() -> promote(ADMIN_USER, OTHER_USER, Rank.RANK_SUPERUSER)),
                Empty.getDefaultInstance());
        asserter.assertThat(() -> user(OTHER_USER), target -> assertEquals(User.Rank.ADMIN, target.getRank()));
    }

    @Test
    @RunOnVertxContext
    public void testStaffToCitizen(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> withRank(OTHER_USER, User.Rank.STAFF)
                        .chain(() -> promote(ADMIN_USER, OTHER_USER, Rank.RANK_CITIZEN)),
                Empty.getDefaultInstance());
        asserter.assertThat(() -> user(OTHER_USER), target -> assertEquals(User.Rank.CITIZEN, target.getRank()));
    }

    @Test
    @RunOnVertxContext
    public void testCitizenToSuperuser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> promote(ADMIN_USER, OTHER_USER, Rank.RANK_SUPERUSER),
                Status.Code.PERMISSION_DENIED,
                "unsupported_promotion");
    }

    @Test
    @RunOnVertxContext
    public void testUnspecifiedRank(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> promote(ADMIN_USER, OTHER_USER, Rank.RANK_UNSPECIFIED),
                Status.Code.INVALID_ARGUMENT,
                "rank_unspecified");
    }

    @Test
    @RunOnVertxContext
    public void testNotSuperuser(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> promote(STAFF_USER, OTHER_USER, Rank.RANK_STAFF),
                Status.Code.PERMISSION_DENIED,
                "caller_rank_insufficient");
    }

    @Test
    @RunOnVertxContext
    public void testSelf(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> promote(ADMIN_USER, ADMIN_USER, Rank.RANK_STAFF),
                Status.Code.PERMISSION_DENIED,
                "user_is_caller");
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ADMIN_USER)
                        .flatMap(admin -> userService(token(admin))
                                .promote(Promotion.newBuilder()
                                        .setId(toProtoService.byteString(UUID.randomUUID()))
                                        .setRank(Rank.RANK_STAFF)
                                        .build())),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testSuperuserToStaff(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> promote(ADMIN_USER, OTHER_USER, Rank.RANK_STAFF).chain(() -> user(OTHER_USER)),
                target -> assertEquals(User.Rank.STAFF, target.getRank()));
    }

    @Test
    @RunOnVertxContext
    public void testSuperuserToCitizen(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> withRank(OTHER_USER, User.Rank.STAFF)
                        .chain(() -> promote(ADMIN_USER, OTHER_USER, Rank.RANK_CITIZEN))
                        .chain(() -> user(OTHER_USER)),
                target -> assertEquals(User.Rank.CITIZEN, target.getRank()));
    }

    @Test
    @RunOnVertxContext
    public void testDeletedTarget(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter, () -> promote(ADMIN_USER, "deleted_user", Rank.RANK_STAFF), Status.Code.NOT_FOUND, null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService()
                        .promote(Promotion.newBuilder()
                                .setId(toProtoService.byteString(UUID.randomUUID()))
                                .setRank(Rank.RANK_STAFF)
                                .build()),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    private @Nonnull Uni<Empty> promote(
            @Nonnull String callerUsername, @Nonnull String targetUsername, @Nonnull Rank rank) {
        return joinAsTuple(user(callerUsername), user(targetUsername))
                .flatMap(pair -> userService(token(pair.getItem1()))
                        .promote(Promotion.newBuilder()
                                .setId(toProtoService.byteString(pair.getItem2().getId()))
                                .setRank(rank)
                                .build()));
    }

    private @Nonnull Uni<Void> withRank(@Nonnull String username, @Nonnull User.Rank rank) {
        return user(username)
                .flatMap(u -> transaction(() -> userRepository.updateRankByUserId(u.getId(), rank)))
                .replaceWithVoid();
    }
}
