package app.fyreplace.grpc.endpoints.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.ImageChunk;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.Nonnull;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class UpdateAvatarTests extends UserTestsBase {
    @Test
    @RunOnVertxContext
    public void testValid(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active)).updateAvatar(pngChunks())),
                image -> {
                    assertFalse(image.getUrl().isEmpty());
                    assertTrue(image.getWidth() > 0);
                    assertTrue(image.getHeight() > 0);
                });
    }

    @Test
    @RunOnVertxContext
    public void testEmptyRemovesAvatar(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active))
                                .updateAvatar(pngChunks())
                                .replaceWith(active)
                                .flatMap(a -> userService(token(a))
                                        .updateAvatar(Multi.createFrom().empty()))),
                image -> {
                    assertEquals("", image.getUrl());
                    assertEquals(0, image.getWidth());
                    assertEquals(0, image.getHeight());
                });
    }

    @Test
    @RunOnVertxContext
    public void testBadFormat(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> userService(token(active)).updateAvatar(textChunks())),
                Status.Code.INVALID_ARGUMENT,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(asserter, () -> userService().updateAvatar(pngChunks()), Status.Code.UNAUTHENTICATED, null);
    }

    private static @Nonnull Multi<ImageChunk> pngChunks() {
        return Multi.createFrom()
                .item(ImageChunk.newBuilder()
                        .setData(ByteString.copyFrom(pngBytes()))
                        .build());
    }

    private static @Nonnull Multi<ImageChunk> textChunks() {
        return Multi.createFrom()
                .item(ImageChunk.newBuilder()
                        .setData(ByteString.copyFromUtf8("not an image"))
                        .build());
    }

    private static byte[] pngBytes() {
        try {
            final var image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            final var baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not create test PNG", e);
        }
    }
}
