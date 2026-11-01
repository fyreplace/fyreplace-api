package app.fyreplace.data.entities;

import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.persistence.FetchType;
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
public abstract class SubscriptionBase extends EntityBase {
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public User user;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public Post post;

    public interface SubscriptionRepositoryBase<T extends SubscriptionBase> extends RepositoryBase<T> {
        @Nonnull
        String getDateSortField();

        default @Nonnull PanacheQuery<T> findByUserId(@Nonnull UUID userId, boolean ascending) {
            final var direction = ascending ? Sort.Direction.Ascending : Sort.Direction.Descending;
            final var sort = Sort.by(getDateSortField(), "id").direction(direction);
            return find("user.id", sort, userId);
        }

        default @Nonnull Uni<Long> countByUserId(@Nonnull UUID userId) {
            return count("user.id", userId);
        }

        default @Nonnull Uni<T> findByUserIdAndPostIdOrNull(@Nonnull UUID userId, @Nonnull UUID postId) {
            return find("user.id = ?1 AND post.id = ?2", userId, postId).firstResult();
        }
    }
}
