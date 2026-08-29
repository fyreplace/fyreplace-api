package app.fyreplace.services.storage.s3;

import jakarta.annotation.Nonnull;
import java.util.List;

public record Policy(@Nonnull String Version, @Nonnull List<Statement> Statement) {
    public static final String CURRENT_VERSION = "2012-10-17";

    public record Statement(
            @Nonnull String Effect,
            @Nonnull String Principal,
            @Nonnull String Action,
            @Nonnull String Resource) {}
}
