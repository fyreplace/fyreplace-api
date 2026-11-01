package app.fyreplace.services.storage.file;

import app.fyreplace.services.storage.LocalStorageServiceBase;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import io.vertx.core.file.FileSystemException;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.NoSuchFileException;

@ApplicationScoped
@Unremovable
@IfBuildProperty(name = "app.storage.type", stringValue = "file")
public final class FileStorageService extends LocalStorageServiceBase {
    @Inject
    FileStorageConfig config;

    @Inject
    Vertx vertx;

    @Override
    public @Nonnull Uni<byte[]> fetch(@Nonnull String path) {
        return vertx.fileSystem().readFile(getFullPath(path)).map(Buffer::getBytes);
    }

    @Override
    public @Nonnull Uni<Void> store(@Nonnull String path, @Nonnull byte[] data) {
        return vertx.fileSystem().writeFile(getFullPath(path), Buffer.buffer(data));
    }

    @Override
    public @Nonnull Uni<Void> remove(@Nonnull String path) {
        return vertx.fileSystem()
                .delete(getFullPath(path))
                .onFailure(failure -> failure instanceof FileSystemException fsException
                        && fsException.getCause() instanceof NoSuchFileException)
                .recoverWithNull()
                .replaceWithVoid();
    }

    private @Nonnull String getFullPath(@Nonnull String path) {
        return config.path().resolve(path).toString();
    }
}
