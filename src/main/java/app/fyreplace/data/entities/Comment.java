package app.fyreplace.data.entities;

import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Column;
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
@Table(name = "comments")
public final class Comment extends AuthoredEntityBase implements Comparable<Comment> {
    public static final int TEXT_MAX_LENGTH = 1500;

    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public Post post;

    @Column(length = TEXT_MAX_LENGTH, nullable = false)
    public String text;

    @Override
    public int compareTo(@Nonnull Comment other) {
        final var dateComparison = dateCreated.compareTo(other.dateCreated);
        return dateComparison != 0 ? dateComparison : id.compareTo(other.id);
    }

    @ApplicationScoped
    public static final class Repository implements AuthoredEntityRepositoryBase<Comment> {
        public @Nonnull PanacheQuery<Comment> findByPostId(@Nonnull UUID postId) {
            return findByPostId(postId, true);
        }

        public @Nonnull PanacheQuery<Comment> findByPostId(@Nonnull UUID postId, boolean ascending) {
            final var direction = ascending ? Sort.Direction.Ascending : Sort.Direction.Descending;
            return find("post.id", Comment.SORTING.direction(direction), postId);
        }

        public @Nonnull PanacheQuery<Comment> findByUserId(@Nonnull UUID userId) {
            return find("author.id", Comment.SORTING, userId);
        }

        public @Nonnull Uni<Long> countByPostId(@Nonnull UUID postId) {
            return count("post.id", postId);
        }
    }
}
