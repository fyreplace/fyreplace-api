package app.fyreplace.services.storage;

import app.fyreplace.services.StorageService;
import jakarta.annotation.Nonnull;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public abstract class LocalStorageServiceBase implements StorageService {
    @ConfigProperty(name = "app.url")
    URI appUrl;

    @Override
    public @Nonnull URI getUri(@Nonnull String path) {
        return UriBuilder.fromUri(appUrl).path(getPathBase()).path(path).build();
    }

    @Override
    public @Nonnull String getPath(@Nonnull URI uri) {
        return uri.getPath().substring(getPathBase().length() + 1);
    }

    private @Nonnull String getPathBase() {
        return "stored_files";
    }
}
