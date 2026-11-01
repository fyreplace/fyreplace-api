package app.fyreplace.services.gravatar;

import app.fyreplace.data.entities.User;
import app.fyreplace.errors.InvalidArgumentException;
import app.fyreplace.errors.NotFoundException;
import app.fyreplace.services.ExecutorService;
import app.fyreplace.services.ImageService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
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
import java.util.UUID;
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

    @ConfigProperty(name = "app.gravatar.enabled")
    boolean enabled;

    @ConfigProperty(name = "app.gravatar.base-url")
    String baseUrl;

    @Inject
    ExecutorService executorService;

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
        final var url = baseUrl;

        if (!enabled) {
            return Uni.createFrom().voidItem();
        }

        return Uni.createFrom()
                .voidItem()
                .emitOn(executorService.safeExecutor())
                .flatMap(_ -> Panache.withSession(() -> findUser(userId)))
                .flatMap(user -> avatarDataOrNull(user, url))
                .emitOn(executorService.safeExecutor())
                .flatMap(data -> storeAvatarIfPresent(userId, data))
                .replaceWithVoid();
    }

    private @Nonnull Uni<User> findUser(@Nonnull UUID userId) {
        return userRepository.findById(userId).onItem().ifNull().failWith(NotFoundException::new);
    }

    private @Nonnull Uni<byte[]> avatarDataOrNull(@Nonnull User user, @Nonnull String baseUrl) {
        if (user.getAvatar() != null || user.getEmail() == null) {
            return Uni.createFrom().nullItem();
        }

        return api(baseUrl)
                .getAvatar(sha256Hex(user.getEmail()), DEFAULT_IMAGE_NOT_FOUND, AVATAR_SIZE)
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

    private @Nonnull GravatarApi api(@Nonnull String baseUrl) {
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(baseUrl))
                .connectTimeout(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .build(GravatarApi.class);
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
