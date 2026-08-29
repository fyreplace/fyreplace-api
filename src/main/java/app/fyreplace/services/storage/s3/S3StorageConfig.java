package app.fyreplace.services.storage.s3;

import io.smallrye.config.ConfigMapping;
import jakarta.annotation.Nonnull;
import java.net.URI;
import java.util.Optional;

@ConfigMapping(prefix = "app.storage.s3")
public interface S3StorageConfig {
    @Nonnull
    String bucket();

    @Nonnull
    Optional<URI> customEndpoint();
}
