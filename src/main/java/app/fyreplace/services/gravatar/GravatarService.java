package app.fyreplace.services.gravatar;

import app.fyreplace.data.entities.User;
import app.fyreplace.errors.InvalidArgumentException;
import app.fyreplace.errors.NotFoundException;
import app.fyreplace.services.ImageService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.logging.Logger;

@ApplicationScoped
public final class GravatarService {
    private static final Logger LOG = Logger.getLogger(GravatarService.class);
    private static final int AVATAR_SIZE = 256;
    private static final String DEFAULT_IMAGE_NOT_FOUND = "404";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @ConfigProperty(name = "app.gravatar.base-url")
    Optional<String> baseUrl;

    @Inject
    Vertx vertx;

    @Inject
    User.Repository userRepository;

    @Inject
    ImageService imageService;

    public void fetchDefaultAvatarDetached(@Nonnull UUID userId) {
        fetchDefaultAvatar(userId)
                .subscribe()
                .with(_ -> {}, failure -> LOG.errorf(failure, "Failed to fetch the default avatar of user %s", userId));
    }

    public @Nonnull Uni<Void> fetchDefaultAvatar(@Nonnull UUID userId) {
        if (baseUrl.isEmpty() || baseUrl.get().isBlank()) {
            return Uni.createFrom().voidItem();
        }

        return Uni.createFrom()
                .voidItem()
                .emitOn(safeExecutor())
                .flatMap(_ -> Panache.withSession(() -> findUser(userId)))
                .flatMap(this::avatarDataOrNull)
                .emitOn(safeExecutor())
                .flatMap(data -> storeAvatarIfPresent(userId, data))
                .replaceWithVoid();
    }

    private @Nonnull Uni<User> findUser(@Nonnull UUID userId) {
        return userRepository.findById(userId).onItem().ifNull().failWith(NotFoundException::new);
    }

    private @Nonnull Uni<byte[]> avatarDataOrNull(@Nonnull User user) {
        if (user.getAvatar() != null || user.getEmail() == null) {
            return Uni.createFrom().nullItem();
        }

        return api().getAvatar(sha256Hex(user.getEmail()), DEFAULT_IMAGE_NOT_FOUND, AVATAR_SIZE)
                .onFailure(WebApplicationException.class)
                .recoverWithNull()
                .flatMap(this::validImageDataOrNull);
    }

    private @Nonnull Uni<byte[]> validImageDataOrNull(@Nullable byte[] data) {
        return data != null && imageService.isValidImage(data)
                ? imageService
                        .shrinkImage(data)
                        .onFailure(InvalidArgumentException.class)
                        .recoverWithNull()
                : Uni.createFrom().nullItem();
    }

    private @Nonnull Uni<Void> storeAvatarIfPresent(@Nonnull UUID userId, @Nullable byte[] data) {
        if (data == null) {
            return Uni.createFrom().voidItem();
        }

        return Panache.withTransaction(() -> findUser(userId)
                .call(user -> user.getAvatar() != null
                        ? Uni.createFrom().voidItem()
                        : userRepository.storeOrRemoveAvatarById(userId, data).replaceWithVoid())
                .replaceWithVoid());
    }

    private @Nonnull GravatarApi api() {
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(baseUrl.orElseThrow()))
                .connectTimeout(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .build(GravatarApi.class);
    }

    private @Nonnull Executor safeExecutor() {
        final var context = VertxContext.getOrCreateDuplicatedContext(vertx.getOrCreateContext());
        VertxContextSafetyToggle.setContextSafe(context, true);
        return command -> context.runOnContext(_ -> command.run());
    }

    static @Nonnull String sha256Hex(@Nonnull String email) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            final var normalized = email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(normalized));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
