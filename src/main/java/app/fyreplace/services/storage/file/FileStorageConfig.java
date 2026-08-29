package app.fyreplace.services.storage.file;

import io.smallrye.config.ConfigMapping;
import jakarta.annotation.Nonnull;
import java.nio.file.Path;

@ConfigMapping(prefix = "app.storage.file")
public interface FileStorageConfig {
    @Nonnull
    Path path();
}
