package app.fyreplace.data.entities;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class SoftDeletableEntityBase extends TimestampedEntityBase {
    @Column(nullable = false)
    public boolean isDeleted = false;

    public interface SoftDeleteRepositoryBase<T extends SoftDeletableEntityBase> extends RepositoryBase<T> {
        default @Nonnull Uni<Integer> softDeleteById(@Nonnull UUID id) {
            return update("isDeleted = TRUE WHERE id = ?1", id);
        }
    }
}
