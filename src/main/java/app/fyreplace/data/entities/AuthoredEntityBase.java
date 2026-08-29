package app.fyreplace.data.entities;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@MappedSuperclass
public abstract class AuthoredEntityBase extends SoftDeletableEntityBase {
    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public User author;

    @Column(nullable = false)
    public boolean anonymous = false;

    public interface AuthoredEntityRepositoryBase<T extends AuthoredEntityBase> extends SoftDeleteRepositoryBase<T> {
        default @Nonnull Uni<Long> countByAuthorId(@Nonnull UUID userId) {
            return count("author.id", userId);
        }
    }
}
