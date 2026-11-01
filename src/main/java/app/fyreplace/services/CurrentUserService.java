package app.fyreplace.services;

import app.fyreplace.data.entities.User;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@RequestScoped
public final class CurrentUserService {
    @Inject
    User.Repository userRepository;

    @Inject
    SecurityIdentity securityIdentity;

    public @Nonnull UUID getUserId() {
        final var userId = getUserIdOrNull();

        if (userId == null) {
            throw new IllegalStateException("The current identity has no JWT principal");
        }

        return userId;
    }

    public @Nullable UUID getUserIdOrNull() {
        if (securityIdentity.getPrincipal() instanceof JsonWebToken jwt) {
            return jwt.<String>claim("user_id").map(UUID::fromString).orElse(null);
        }

        return null;
    }

    public @Nonnull Uni<User> getUser() {
        return userRepository.findById(getUserId());
    }
}
