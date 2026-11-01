package app.fyreplace.services.storage.memory;

import app.fyreplace.services.storage.LocalStorageServiceBase;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.properties.IfBuildProperty;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@Unremovable
@IfBuildProperty(name = "app.storage.type", stringValue = "memory")
public final class MemoryStorageService extends LocalStorageServiceBase {
    private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

    @Override
    public @Nonnull Uni<byte[]> fetch(@Nonnull String path) {
        if (!storage.containsKey(path)) {
            return Uni.createFrom().failure(new IOException("No such file or directory: " + path));
        }

        return Uni.createFrom().item(storage.get(path));
    }

    @Override
    public @Nonnull Uni<Void> store(@Nonnull String path, @Nonnull byte[] data) {
        storage.put(path, data);
        return Uni.createFrom().voidItem();
    }

    @Override
    public @Nonnull Uni<Void> remove(@Nonnull String path) {
        storage.remove(path);
        return Uni.createFrom().voidItem();
    }
}
