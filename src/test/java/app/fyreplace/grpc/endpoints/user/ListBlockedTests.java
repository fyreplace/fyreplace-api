package app.fyreplace.grpc.endpoints.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Header;
import app.fyreplace.protos.Page;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class ListBlockedTests extends UserTestsBase {
    @Test
    @RunOnVertxContext
    public void testEmpty(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .listBlocked(paginateRequest(10))
                                .collect()
                                .asList()),
                bundles -> {
                    assertEquals(1, bundles.size());
                    assertEquals(0, bundles.getFirst().getProfilesCount());
                });
    }

    @Test
    @RunOnVertxContext
    public void testWithBlocked(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER))
                        .flatMap(pair -> userService(token(pair.getItem1()))
                                .updateBlock(app.fyreplace.protos.Block.newBuilder()
                                        .setId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setBlocked(true)
                                        .build())
                                .replaceWith(pair.getItem1()))
                        .flatMap(active -> userService(token(active))
                                .listBlocked(paginateRequest(10))
                                .collect()
                                .asList()),
                bundles -> {
                    assertEquals(1, bundles.size());
                    final var profiles = bundles.getFirst().getProfilesList();
                    assertEquals(1, profiles.size());
                    assertEquals(OTHER_USER, profiles.getFirst().getUsername());
                });
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService().listBlocked(paginateRequest(10)).collect().asList(),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testPagination(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> blockUsers(active, OTHER_USER, TARGET_USER, BAN_TARGET)
                                .chain(() -> userService(token(active))
                                        .listBlocked(paginateRequest(2, 0, 1))
                                        .collect()
                                        .asList())),
                bundles -> {
                    assertEquals(2, bundles.size());
                    assertEquals(2, bundles.get(0).getProfilesCount());
                    assertTrue(bundles.get(0).hasNext());
                    assertEquals(1, bundles.get(1).getProfilesCount());
                    assertTrue(bundles.get(1).hasPrevious());
                });
    }

    @Test
    @RunOnVertxContext
    public void testOutOfBounds(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER))
                        .flatMap(pair -> userService(token(pair.getItem1()))
                                .updateBlock(app.fyreplace.protos.Block.newBuilder()
                                        .setId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setBlocked(true)
                                        .build())
                                .replaceWith(pair.getItem1()))
                        .flatMap(active -> userService(token(active))
                                .listBlocked(paginateRequest(10, 5))
                                .collect()
                                .asList()),
                bundles -> {
                    assertEquals(1, bundles.size());
                    assertEquals(0, bundles.getFirst().getProfilesCount());
                    assertFalse(bundles.getFirst().hasNext());
                });
    }

    @Test
    @RunOnVertxContext
    public void testInvalidSize(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .listBlocked(Multi.createFrom()
                                        .items(Page.newBuilder()
                                                .setHeader(Header.newBuilder()
                                                        .setForward(true)
                                                        .setSize(0))
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
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .listBlocked(Multi.createFrom()
                                        .items(Page.newBuilder()
                                                .setCursor(toProtoService.cursor(
                                                        new io.quarkus.panache.common.Page(0, 10)))
                                                .build()))
                                .collect()
                                .asList()),
                Status.Code.INVALID_ARGUMENT,
                "missing_header");
    }

    private @Nonnull io.smallrye.mutiny.Uni<Void> blockUsers(
            @Nonnull app.fyreplace.data.entities.User issuer, @Nonnull String... targetUsernames) {
        io.smallrye.mutiny.Uni<Void> uni = io.smallrye.mutiny.Uni.createFrom().voidItem();
        for (final var targetUsername : targetUsernames) {
            uni = uni.chain(() -> user(targetUsername)
                    .flatMap(target -> transaction(() -> {
                        final var block = new app.fyreplace.data.entities.Block();
                        block.setIssuer(issuer);
                        block.setTarget(target);
                        return blockRepository.persist(block).replaceWithVoid();
                    })));
        }
        return uni;
    }

    private @Nonnull Multi<Page> paginateRequest(int size) {
        return paginateRequest(size, 0);
    }

    private @Nonnull Multi<Page> paginateRequest(int size, int... indexes) {
        final var pages = new java.util.ArrayList<Page>();
        pages.add(Page.newBuilder()
                .setHeader(Header.newBuilder().setForward(true).setSize(size))
                .build());
        for (final var index : indexes) {
            pages.add(Page.newBuilder()
                    .setCursor(toProtoService.cursor(new io.quarkus.panache.common.Page(index, size)))
                    .build());
        }
        return Multi.createFrom().items(pages.stream());
    }
}
