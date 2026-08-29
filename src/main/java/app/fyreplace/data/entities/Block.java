package app.fyreplace.data.entities;

import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "blocks", uniqueConstraints = @UniqueConstraint(columnNames = {"issuer_id", "target_id"}))
@NamedQueries({@NamedQuery(name = Block.Q_FIND_BY_ISSUER_AND_TARGET, query = """
    FROM Block
    WHERE issuer.id = :issuerId AND target.id = :targetId
    """)})
public final class Block extends EntityBase {
    public static final String Q_FIND_BY_ISSUER_AND_TARGET = "Block.isBlocking";

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @OnDelete(action = OnDeleteAction.CASCADE)
    User issuer;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @OnDelete(action = OnDeleteAction.CASCADE)
    User target;

    @ApplicationScoped
    public static final class Repository implements RepositoryBase<Block> {
        public @Nonnull PanacheQuery<Block> findByIssuerId(@Nonnull UUID issuerId, boolean ascending) {
            final var direction = ascending ? Sort.Direction.Ascending : Sort.Direction.Descending;
            return find("issuer.id", Sort.by("target.username", direction), issuerId);
        }

        public @Nonnull Uni<Long> countByIssuerId(@Nonnull UUID issuerId) {
            return count("issuer.id", issuerId);
        }

        public @Nonnull Uni<Boolean> isBlocking(@Nonnull UUID issuerId, @Nonnull UUID targetId) {
            return find("#" + Block.Q_FIND_BY_ISSUER_AND_TARGET, Map.of("issuerId", issuerId, "targetId", targetId))
                    .count()
                    .map(c -> c != 0);
        }

        public @Nonnull Uni<Long> deleteByIssuerIdAndTargetId(@Nonnull UUID issuerId, @Nonnull UUID targetId) {
            return delete("issuer.id = ?1 AND target.id = ?2", issuerId, targetId);
        }
    }
}
