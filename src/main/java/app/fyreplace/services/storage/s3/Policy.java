package app.fyreplace.services.storage.s3;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.List;

public record Policy(
        @JsonProperty("Version") @Nonnull String version,
        @JsonProperty("Statement") @Nonnull List<Statement> statement) {
    public static final String CURRENT_VERSION = "2012-10-17";

    public record Statement(
            @JsonProperty("Effect") @Nonnull String effect,
            @JsonProperty("Principal") @Nonnull String principal,
            @JsonProperty("Action") @Nonnull String action,
            @JsonProperty("Resource") @Nonnull String resource) {}
}
