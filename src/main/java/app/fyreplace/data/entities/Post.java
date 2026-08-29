package app.fyreplace.data.entities;

import app.fyreplace.services.CurrentUserService;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Formula;

@Getter
@Setter
@Entity
@Table(name = "posts")
@NamedQueries({@NamedQuery(name = Post.Q_FIND_CURRENT_USER_FEED, query = """
    FROM Post AS posts
    WHERE author.id != :userId
        AND dateCreated >= CURRENT_TIMESTAMP - 7 DAY
        AND isPublished
        AND NOT isDeleted
        AND NOT author.isDeleted
        AND life > 0
        AND NOT EXISTS (
            SELECT id FROM Vote AS votes
            WHERE votes.user.id = :userId
                AND votes.post.id = posts.id
        )
        AND NOT EXISTS (
            SELECT id FROM Block AS blocks
            WHERE (blocks.issuer.id = :userId AND blocks.target.id = posts.author.id)
                OR (blocks.issuer.id = posts.author.id AND blocks.target.id = :userId)
        )
    """)})
public final class Post extends AuthoredEntityBase {
    public static final String Q_FIND_CURRENT_USER_FEED = "Post.findCurrentUserFeed";
    public static final int INITIAL_LIFE = 5;

    @Column(nullable = false)
    public boolean isPublished = false;

    @Column(nullable = false)
    public int life = 0;

    @OneToMany(mappedBy = "post", fetch = FetchType.EAGER)
    @OrderBy("position")
    public List<Chapter> chapters;

    @Formula("(SELECT count(*) FROM comments WHERE comments.post_id = id)")
    public long commentCount;

    @Formula("(SELECT count(*) FROM votes WHERE votes.post_id = id)")
    public long voteCount;

    @ApplicationScoped
    public static final class Repository implements AuthoredEntityRepositoryBase<Post> {
        @Inject
        CurrentUserService currentUserService;

        public @Nonnull PanacheQuery<Post> findByAuthorId(@Nonnull UUID authorId) {
            return find("author.id = ?1 AND NOT isDeleted", Post.SORTING, authorId);
        }

        public @Nonnull PanacheQuery<Post> findAllByAuthorId(@Nonnull UUID authorId) {
            return find("author.id", Post.SORTING, authorId);
        }

        public @Nonnull PanacheQuery<Post> findByAuthorId(
                @Nonnull UUID authorId, boolean published, boolean ascending) {
            final var direction = ascending ? Sort.Direction.Ascending : Sort.Direction.Descending;
            return find(
                    "author.id = ?1 AND NOT isDeleted AND isPublished = ?2",
                    Post.SORTING.direction(direction),
                    authorId,
                    published);
        }

        public @Nonnull PanacheQuery<Post> findCurrentUserFeed() {
            return find(
                    "#" + Post.Q_FIND_CURRENT_USER_FEED,
                    Sort.by("life", "dateCreated"),
                    Map.of("userId", currentUserService.getUserId()));
        }

        public @Nonnull Uni<Integer> updatePublishedById(@Nonnull UUID postId) {
            return update(
                    "dateCreated = CURRENT_TIMESTAMP, isPublished = TRUE, life = ?2 WHERE id = ?1",
                    postId,
                    INITIAL_LIFE);
        }
    }
}
