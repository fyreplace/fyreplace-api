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
import java.util.Map;
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
    @OnDelete(action = OnDeleteAction.SET_NULL)
    public Comment lastCommentSeen;

    @ApplicationScoped
    public static final class Repository implements SubscriptionRepositoryBase<Subscription> {
        @Override
        public @Nonnull String getDateSortField() {
            return "dateLastSeen";
        }

        public @Nonnull Uni<Void> updateLastCommentSeenByUserId(@Nonnull UUID userId, @Nonnull Comment comment) {
            return executeNative(
                    """
                    UPDATE subscriptions
                    SET last_comment_seen_id = :commentId
                    WHERE user_id = :userId
                      AND post_id = :postId
                      AND (
                          last_comment_seen_id IS NULL
                          OR (SELECT (c.date_created, c.id) FROM comments c WHERE c.id = subscriptions.last_comment_seen_id)
                             < (SELECT (c.date_created, c.id) FROM comments c WHERE c.id = :commentId)
                      )
                    """,
                    Map.of(
                            "userId", userId,
                            "postId", comment.getPost().getId(),
                            "commentId", comment.getId()));
        }

        public @Nonnull Uni<Void> markAllReadByUserId(@Nonnull UUID userId) {
            return executeNative(
                    "UPDATE subscriptions SET last_comment_seen_id = (SELECT id FROM comments WHERE post_id = subscriptions.post_id ORDER BY date_created DESC, id DESC LIMIT 1) WHERE user_id = :userId",
                    Map.of("userId", userId));
        }

        public @Nonnull Uni<Integer> updateLastSeenByUserIdAndPostId(@Nonnull UUID userId, @Nonnull UUID postId) {
            return update("dateLastSeen = CURRENT_TIMESTAMP WHERE user.id = ?1 AND post.id = ?2", userId, postId);
        }

        public @Nonnull Uni<Integer> updateLastSeenById(@Nonnull UUID id, @Nonnull OffsetDateTime dateLastSeen) {
            return update("dateLastSeen = ?2 WHERE id = ?1", id, dateLastSeen);
        }

        public @Nonnull Uni<Long> deleteByUserId(@Nonnull UUID userId) {
            return delete("user.id", userId);
        }

        public @Nonnull Uni<Long> deleteByUserIdAndPostId(@Nonnull UUID userId, @Nonnull UUID postId) {
            return delete("user.id = ?1 AND post.id = ?2", userId, postId);
        }

        public @Nonnull Uni<Void> persistIfNotExists(@Nonnull Subscription subscription) {
            return executeNative(
                    "SELECT subscriptions__insert_if_absent(:userId, :postId)",
                    Map.of(
                            "userId",
                            subscription.getUser().getId(),
                            "postId",
                            subscription.getPost().getId()));
        }
    }
}
