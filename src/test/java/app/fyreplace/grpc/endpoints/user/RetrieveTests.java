package app.fyreplace.grpc.endpoints.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Rank;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class RetrieveTests extends UserTestsBase {
    @Test
    @RunOnVertxContext
    public void testAuthenticatedNonSelf(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER))
                        .flatMap(pair -> userService(token(pair.getItem1()))
                                .retrieve(toProtoService.id(pair.getItem2().getId()))),
                retrieved -> {
                    assertEquals(OTHER_USER, retrieved.getProfile().getUsername());
                    assertEquals(Rank.RANK_CITIZEN, retrieved.getProfile().getRank());
                    assertFalse(retrieved.getProfile().getIsDeleted());
                    assertFalse(retrieved.getProfile().getIsBanned());
                    assertFalse(retrieved.getProfile().getIsBlocked());
                    assertEquals("", retrieved.getEmail());
                    assertEquals(0, retrieved.getBlockedUsers());
                });
    }

    @Test
    @RunOnVertxContext
    public void testAnonymous(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER).flatMap(other -> userService().retrieve(toProtoService.id(other.getId()))),
                retrieved -> {
                    assertEquals(OTHER_USER, retrieved.getProfile().getUsername());
                    assertEquals("", retrieved.getEmail());
                    assertEquals(0, retrieved.getBlockedUsers());
                    assertFalse(retrieved.getProfile().getIsBlocked());
                });
    }

    @Test
    @RunOnVertxContext
    public void testSelf(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active)).retrieve(toProtoService.id(active.getId()))),
                retrieved -> {
                    assertEquals(ACTIVE_USER, retrieved.getProfile().getUsername());
                    assertEquals("user_0@example.org", retrieved.getEmail());
                    assertEquals("I am user 0", retrieved.getBio());
                });
    }

    @Test
    @RunOnVertxContext
    public void testBlocked(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER))
                        .flatMap(pair -> userService(token(pair.getItem1()))
                                .updateBlock(app.fyreplace.protos.Block.newBuilder()
                                        .setId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setBlocked(true)
                                        .build())
                                .replaceWith(pair)
                                .flatMap(p -> userService(token(p.getItem1()))
                                        .retrieve(toProtoService.id(p.getItem2().getId())))),
                retrieved -> assertTrue(retrieved.getProfile().getIsBlocked()));
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService().retrieve(toProtoService.id(UUID.randomUUID())),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testEmptyId(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService()
                        .retrieve(app.fyreplace.protos.Id.newBuilder()
                                .setId(com.google.protobuf.ByteString.copyFromUtf8("invalid"))
                                .build()),
                Status.Code.INVALID_ARGUMENT,
                "invalid_uuid");
    }

    @Test
    @RunOnVertxContext
    public void testDeleted(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> transaction(() -> {
                            final var victim = new app.fyreplace.data.entities.User();
                            victim.setUsername("delete_victim");
                            victim.setEmail("delete_victim@example.org");
                            victim.setPassword("");
                            victim.setActive(true);
                            return userRepository.persist(victim).replaceWith(victim);
                        })
                        .flatMap(victim -> transaction(() -> userRepository.softDeleteById(victim.getId()))
                                .replaceWith(victim)),
                deleted -> assertFailure(
                        asserter,
                        () -> userService().retrieve(toProtoService.id(deleted.getId())),
                        Status.Code.NOT_FOUND,
                        null));
    }

    @Test
    @RunOnVertxContext
    public void testStaffRank(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(other -> session(() -> userRepository.updateRankByUserId(
                                        other.getId(), app.fyreplace.data.entities.User.Rank.STAFF))
                                .replaceWith(other))
                        .flatMap(other -> userService().retrieve(toProtoService.id(other.getId()))),
                retrieved ->
                        assertEquals(Rank.RANK_STAFF, retrieved.getProfile().getRank()));
    }

    @Test
    @RunOnVertxContext
    public void testSuperuserRank(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(other -> session(() -> userRepository.updateRankByUserId(
                                        other.getId(), app.fyreplace.data.entities.User.Rank.ADMIN))
                                .replaceWith(other))
                        .flatMap(other -> userService().retrieve(toProtoService.id(other.getId()))),
                retrieved ->
                        assertEquals(Rank.RANK_SUPERUSER, retrieved.getProfile().getRank()));
    }

    @Test
    @RunOnVertxContext
    public void testTemporaryBan(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(other -> session(() -> userRepository.updateBanByUserId(other.getId(), 3))
                                .replaceWith(other))
                        .flatMap(other -> userService().retrieve(toProtoService.id(other.getId()))),
                retrieved -> {
                    assertTrue(retrieved.getProfile().getIsBanned());
                    assertEquals(OTHER_USER, retrieved.getProfile().getUsername());
                });
    }

    @Test
    @RunOnVertxContext
    public void testPermanentBan(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(other -> session(() -> userRepository
                                        .updateBanByUserId(other.getId(), 3)
                                        .chain(() -> userRepository.updateBanByUserId(other.getId(), 3)))
                                .replaceWith(other))
                        .flatMap(other -> userService().retrieve(toProtoService.id(other.getId()))),
                retrieved -> {
                    assertTrue(retrieved.getProfile().getIsBanned());
                    assertEquals(Rank.RANK_UNSPECIFIED, retrieved.getProfile().getRank());
                    assertEquals("", retrieved.getProfile().getUsername());
                    assertEquals("", retrieved.getBio());
                    assertEquals("", retrieved.getEmail());
                });
    }
}
