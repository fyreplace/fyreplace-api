package app.fyreplace.services.gravatar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import app.fyreplace.TestsBase;
import app.fyreplace.data.entities.User;
import app.fyreplace.errors.NotFoundException;
import app.fyreplace.services.StorageService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(GravatarServiceTests.GravatarTestProfile.class)
@QuarkusTestResource(GravatarServiceTests.GravatarStubResource.class)
public final class GravatarServiceTests extends TestsBase {
    private static final int STUB_PORT = 18089;
    private static final String IMAGE_EMAIL = "pending_user@example.org";
    private static final String TEXT_EMAIL = "password_user@example.org";
    private static final Set<String> stubHits = ConcurrentHashMap.newKeySet();
    private static final byte[] PNG = pngBytes();

    @Inject
    GravatarService gravatarService;

    @Inject
    User.Repository userRepository;

    @Inject
    StorageService storageService;

    @AfterEach
    void teardown() {
        stubHits.clear();
        awaitOnSafeContext(() -> Uni.createFrom()
                .voidItem()
                .chain(() -> userRepository.findByUsername("pending_user"))
                .call(user -> userRepository.storeOrRemoveAvatarById(user.getId(), new byte[0]))
                .chain(() -> userRepository.findByUsername("password_user"))
                .call(user -> userRepository.storeOrRemoveAvatarById(user.getId(), new byte[0]))
                .replaceWithVoid());
    }

    @Test
    @RunOnVertxContext
    public void testSuccess(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user("pending_user")
                        .flatMap(user ->
                                gravatarService.fetchDefaultAvatar(user.getId()).replaceWith(user.getId()))
                        .flatMap(id -> session(() -> userRepository.findById(id)))
                        .flatMap(user ->
                                storageService.fetch(user.getAvatar().getPath()).map(data -> Tuple2.of(user, data))),
                pair -> {
                    assertNotNull(pair.getItem1().getAvatar());
                    assertArrayEquals(PNG, pair.getItem2());
                });
    }

    @Test
    @RunOnVertxContext
    public void testNoGravatar(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user("passwordless_user")
                        .flatMap(user ->
                                gravatarService.fetchDefaultAvatar(user.getId()).replaceWith(user.getId()))
                        .flatMap(id -> session(() -> userRepository.findById(id))),
                user -> assertNull(user.getAvatar()));
    }

    @Test
    @RunOnVertxContext
    public void testBadMime(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user("password_user")
                        .flatMap(user ->
                                gravatarService.fetchDefaultAvatar(user.getId()).replaceWith(user.getId()))
                        .flatMap(id -> session(() -> userRepository.findById(id))),
                user -> assertNull(user.getAvatar()));
    }

    @Test
    @RunOnVertxContext
    public void testExistingAvatarKept(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user("pending_user")
                        .flatMap(user -> transaction(() -> userRepository.storeOrRemoveAvatarById(user.getId(), PNG))
                                .replaceWith(user.getId()))
                        .flatMap(id -> session(() -> userRepository.findById(id)))
                        .flatMap(before -> gravatarService
                                .fetchDefaultAvatar(before.getId())
                                .replaceWith(before.getAvatar().getPath()))
                        .flatMap(path -> session(() -> userRepository.findByUsername("pending_user"))
                                .map(after -> Tuple2.of(path, after.getAvatar().getPath()))),
                pair -> {
                    assertEquals(pair.getItem1(), pair.getItem2());
                    assertFalse(stubHits.contains(GravatarService.sha256Hex(IMAGE_EMAIL)));
                });
    }

    @Test
    @RunOnVertxContext
    public void testUnknownUser(@Nonnull UniAsserter asserter) {
        asserter.assertFailedWith(
                () -> gravatarService.fetchDefaultAvatar(UUID.randomUUID()),
                failure -> assertInstanceOf(NotFoundException.class, failure));
    }

    private @Nonnull Uni<User> user(@Nonnull String username) {
        return session(() -> userRepository.findByUsername(username));
    }

    private static void handleStubRequest(@Nonnull HttpServerRequest request) {
        final var hash = request.path().substring("/avatar/".length());
        stubHits.add(hash);

        if (hash.equals(GravatarService.sha256Hex(IMAGE_EMAIL))) {
            request.response().putHeader("content-type", "image/png").end(Buffer.buffer(PNG));
        } else if (hash.equals(GravatarService.sha256Hex(TEXT_EMAIL))) {
            request.response().putHeader("content-type", "text/plain").end("not an image");
        } else {
            request.response().setStatusCode(404).end();
        }
    }

    private static byte[] pngBytes() {
        try {
            final var image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            final var baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the test PNG", e);
        }
    }

    public static final class GravatarTestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("app.gravatar.base-url", "http://127.0.0.1:" + STUB_PORT);
        }
    }

    public static final class GravatarStubResource implements QuarkusTestResourceLifecycleManager {
        private Vertx vertx;
        private HttpServer server;

        @Override
        public Map<String, String> start() {
            vertx = Vertx.vertx();
            try {
                server = vertx.createHttpServer()
                        .requestHandler(GravatarServiceTests::handleStubRequest)
                        .listen(STUB_PORT, "127.0.0.1")
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Could not start the Gravatar stub", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("Could not start the Gravatar stub", e);
            }
            return Map.of();
        }

        @Override
        public void stop() {
            try {
                if (server != null) {
                    server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
                }
                if (vertx != null) {
                    vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Could not stop the Gravatar stub", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("Could not stop the Gravatar stub", e);
            }
        }
    }
}
