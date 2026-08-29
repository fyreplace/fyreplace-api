package app.fyreplace.data.entities;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "subscriptions", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"}))
public final class Subscription extends SubscriptionBase {
    @Column(nullable = false)
    public OffsetDateTime dateLastSeen;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    public Comment lastCommentSeen;

    @ApplicationScoped
    public static final class Repository implements SubscriptionRepositoryBase<Subscription> {
        @Override
        public @Nonnull String getDateSortField() {
            return "dateLastSeen";
        }

        public @Nonnull Uni<Integer> updateLastCommentSeenByUserId(@Nonnull UUID userId, @Nonnull Comment comment) {
            return update("lastCommentSeen = ?3 WHERE user = ?1 AND post = ?2", userId, comment.getPost(), comment);
        }

        public @Nonnull Uni<Long> deleteByUserId(@Nonnull UUID userId) {
            return delete("user.id", userId);
        }

        public @Nonnull Uni<Long> deleteByUserIdAndPostId(@Nonnull UUID userId, @Nonnull UUID postId) {
            return delete("user.id = ?1 AND post.id = ?2", userId, postId);
        }
    }
}
