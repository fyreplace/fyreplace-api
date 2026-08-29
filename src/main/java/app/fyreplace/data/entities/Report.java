package app.fyreplace.data.entities;

import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "reports")
public final class Report extends EntityBase {
    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public User issuer;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    public User targetUser;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    public Post targetPost;

    @ManyToOne
    @OnDelete(action = OnDeleteAction.CASCADE)
    public Comment targetComment;

    @ApplicationScoped
    public static final class Repository implements RepositoryBase<Report> {
        public @Nonnull PanacheQuery<Report> findByIssuerId(@Nonnull UUID issuerId) {
            return find("issuer.id", issuerId);
        }

        public @Nonnull Uni<Long> deleteByTargetId(@Nonnull UUID targetId) {
            return delete("targetUser.id = ?1 OR targetPost.id = ?1 OR targetComment.id = ?1", targetId);
        }
    }
}
