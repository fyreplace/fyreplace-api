package app.fyreplace.data.entities;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.reactive.mutiny.Mutiny;

@Getter
@Entity
@Table(name = "notifications")
@Immutable
public final class Notification extends SubscriptionBase {
    public OffsetDateTime dateUpdated;

    public long unreadCommentCount;

    @ApplicationScoped
    public static final class Repository implements SubscriptionRepositoryBase<Notification> {
        @Override
        public @Nonnull String getDateSortField() {
            return "dateUpdated";
        }

        public @Nonnull Uni<Long> sumUnreadCommentCountByUserId(@Nonnull UUID userId) {
            return nativeQuery(
                            "SELECT coalesce(sum(unread_comment_count), 0) FROM notifications WHERE user_id = :userId",
                            Map.of("userId", userId))
                    .flatMap(Mutiny.SelectionQuery::getSingleResult)
                    .map(result -> ((Number) result).longValue());
        }
    }
}
