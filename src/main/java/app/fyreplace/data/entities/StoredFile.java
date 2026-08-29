package app.fyreplace.data.entities;

import app.fyreplace.services.StorageService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.nio.file.Path;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "stored_files")
public final class StoredFile extends EntityBase {
    @Column(unique = true, nullable = false)
    public String path;

    public StoredFile(@Nonnull String path) {
        this.path = path;
    }

    @ApplicationScoped
    public static final class Repository implements RepositoryBase<StoredFile> {
        @Inject
        StorageService storageService;

        public @Nonnull Uni<StoredFile> findByPath(@Nonnull String path) {
            return find("path", path).firstResult();
        }

        public @Nonnull Uni<StoredFile> store(@Nonnull String directory, @Nonnull byte[] data) {
            return Uni.createFrom()
                    .item(Path.of(directory, UUID.randomUUID().toString()))
                    .map(Path::toString)
                    .map(StoredFile::new)
                    .call(f -> storageService.store(f.getPath(), data))
                    .call(this::persist);
        }

        public @Nonnull Uni<Void> remove(@Nonnull StoredFile storedFile) {
            return delete(storedFile).call(() -> storageService.remove(storedFile.getPath()));
        }
    }
}
