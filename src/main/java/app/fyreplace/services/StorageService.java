package app.fyreplace.services;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.net.URI;

public interface StorageService {
    @Nonnull
    Uni<byte[]> fetch(@Nonnull String path);

    @Nonnull
    Uni<Void> store(@Nonnull String path, @Nonnull byte[] data);

    @Nonnull
    Uni<Void> remove(@Nonnull String path);

    @Nonnull
    URI getUri(@Nonnull String path);

    @Nonnull
    String getPath(@Nonnull URI uri);
}
