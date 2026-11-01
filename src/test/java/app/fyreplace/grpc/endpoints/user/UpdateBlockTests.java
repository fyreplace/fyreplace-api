package app.fyreplace.grpc.endpoints.user;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Block;
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
public final class UpdateBlockTests extends UserTestsBase {
    @Test
    @RunOnVertxContext
    public void testBlock(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER))
                        .flatMap(pair -> userService(token(pair.getItem1()))
                                .updateBlock(Block.newBuilder()
                                        .setId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setBlocked(true)
                                        .build())),
                Empty.getDefaultInstance());
        asserter.assertThat(
                () -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER))
                        .flatMap(pair -> session(() -> blockRepository.isBlocking(
                                pair.getItem1().getId(), pair.getItem2().getId()))),
                Assertions::assertTrue);
    }

    @Test
    @RunOnVertxContext
    public void testBlockSelf(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .updateBlock(Block.newBuilder()
                                        .setId(toProtoService.byteString(active.getId()))
                                        .setBlocked(true)
                                        .build())),
                Status.Code.PERMISSION_DENIED,
                "user_is_caller");
    }

    @Test
    @RunOnVertxContext
    public void testBlockAlreadyBlocked(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER)).flatMap(pair -> {
                    final var block = Block.newBuilder()
                            .setId(toProtoService.byteString(pair.getItem2().getId()))
                            .setBlocked(true)
                            .build();
                    return userService(token(pair.getItem1()))
                            .updateBlock(block)
                            .replaceWith(pair)
                            .flatMap(p -> userService(token(p.getItem1())).updateBlock(block));
                }),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testUnblock(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER))
                        .flatMap(pair -> userService(token(pair.getItem1()))
                                .updateBlock(Block.newBuilder()
                                        .setId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setBlocked(true)
                                        .build())
                                .replaceWith(pair)
                                .flatMap(p -> userService(token(p.getItem1()))
                                        .updateBlock(Block.newBuilder()
                                                .setId(toProtoService.byteString(
                                                        p.getItem2().getId()))
                                                .setBlocked(false)
                                                .build()))),
                Empty.getDefaultInstance());
        asserter.assertThat(
                () -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER))
                        .flatMap(pair -> session(() -> blockRepository.isBlocking(
                                pair.getItem1().getId(), pair.getItem2().getId()))),
                Assertions::assertFalse);
    }

    @Test
    @RunOnVertxContext
    public void testUnblockNotBlocked(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER))
                        .flatMap(pair -> userService(token(pair.getItem1()))
                                .updateBlock(Block.newBuilder()
                                        .setId(toProtoService.byteString(
                                                pair.getItem2().getId()))
                                        .setBlocked(false)
                                        .build())),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testBlockNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .updateBlock(Block.newBuilder()
                                        .setId(toProtoService.byteString(UUID.randomUUID()))
                                        .setBlocked(true)
                                        .build())),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> userService()
                        .updateBlock(Block.newBuilder()
                                .setId(toProtoService.byteString(UUID.randomUUID()))
                                .setBlocked(true)
                                .build()),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
