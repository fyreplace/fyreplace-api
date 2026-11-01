package app.fyreplace.data.entities;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.reactive.mutiny.Mutiny;

@Getter
@Setter
@Entity
@Table(name = "votes", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"}))
public final class Vote extends EntityBase {
    public static final int SPREAD_LIFE = 3;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public User user;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public Post post;

    @Column(nullable = false)
    public boolean spread;

    @ApplicationScoped
    public static final class Repository implements RepositoryBase<Vote> {
        public @Nonnull Uni<Vote> findByUserIdAndPostId(@Nonnull UUID userId, @Nonnull UUID postId) {
            return find("user.id = ?1 AND post.id = ?2", userId, postId).firstResult();
        }

        public @Nonnull Uni<Vote> castOrNull(@Nonnull Vote vote) {
            return nativeQuery(
                            Vote.class,
                            "SELECT * FROM votes__cast(:userId, :postId, :spread)",
                            Map.of(
                                    "userId",
                                    vote.getUser().getId(),
                                    "postId",
                                    vote.getPost().getId(),
                                    "spread",
                                    vote.isSpread() ? SPREAD_LIFE : 0))
                    .flatMap(Mutiny.SelectionQuery::getSingleResultOrNull);
        }
    }
}
