package app.fyreplace.services;

import app.fyreplace.data.entities.User;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
public final class JwtService {
    @ConfigProperty(name = "app.url")
    URI appUrl;

    @Inject
    JWTParser parser;

    public static final Duration SHELF_LIFE = Duration.ofDays(7);

    public @Nonnull String makeJwt(@Nonnull User user, boolean emailToken) {
        final var userId = user.id.toString();
        var jwt = Jwt.issuer(appUrl.toString())
                .subject(userId)
                .upn(user.username)
                .claim("user_id", userId)
                .expiresIn(SHELF_LIFE);

        if (emailToken) {
            jwt = jwt.claim("timestamp", Instant.now().getEpochSecond());
        }

        return jwt.sign();
    }

    public @Nonnull String makeEmailUpdateJwt(@Nonnull User user, @Nonnull String newEmail) {
        final var userId = user.id.toString();
        return Jwt.issuer(appUrl.toString())
                .subject(userId)
                .upn(user.username)
                .claim("user_id", userId)
                .claim("email", newEmail)
                .claim("timestamp", Instant.now().getEpochSecond())
                .expiresIn(SHELF_LIFE)
                .sign();
    }

    public @Nullable JsonWebToken verifyToken(@Nonnull String jwt) {
        try {
            return parser.parse(jwt);
        } catch (ParseException _) {
            return null;
        }
    }
}
