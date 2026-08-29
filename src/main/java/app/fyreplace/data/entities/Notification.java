package app.fyreplace.data.entities;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

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
    }
}
