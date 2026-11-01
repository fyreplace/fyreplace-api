package app.fyreplace.data.entities;

import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.NoResultException;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
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
@NamedQueries({
    @NamedQuery(name = Post.Q_FIND_CURRENT_USER_FEED, query = """
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
    ORDER BY posts.life, posts.dateCreated
    """),
    @NamedQuery(name = Post.Q_FIND_FEED, query = """
    FROM Post AS posts
    WHERE dateCreated >= CURRENT_TIMESTAMP - 7 DAY
        AND isPublished
        AND NOT isDeleted
        AND NOT author.isDeleted
        AND life > 0
    ORDER BY posts.life, posts.dateCreated
    """)
})
public final class Post extends AuthoredEntityBase {
    public static final String Q_FIND_CURRENT_USER_FEED = "Post.findCurrentUserFeed";
    public static final String Q_FIND_FEED = "Post.findFeed";
    public static final int INITIAL_LIFE = 5;
    public static final int FEED_MAX_SIZE = 10;
    public static final int MAX_CHAPTERS = 10;

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

        public @Nonnull PanacheQuery<Post> findFeed(@Nullable UUID userId) {
            return userId == null
                    ? find("#" + Post.Q_FIND_FEED).page(0, FEED_MAX_SIZE)
                    : find("#" + Post.Q_FIND_CURRENT_USER_FEED, Map.of("userId", userId))
                            .page(0, FEED_MAX_SIZE);
        }

        public @Nonnull Uni<Post> findReadableById(@Nonnull UUID callerId, @Nonnull UUID postId) {
            return findReadableByIdOrNull(callerId, postId).onItem().ifNull().failWith(NoResultException::new);
        }

        public @Nonnull Uni<Post> findReadableByIdOrNull(@Nullable UUID callerId, @Nonnull UUID postId) {
            return find("id = ?1 AND NOT isDeleted AND (isPublished = TRUE OR author.id = ?2)", postId, callerId)
                    .firstResult();
        }

        public @Nonnull Uni<Long> countByAuthorId(@Nonnull UUID authorId, boolean published) {
            return count("author.id = ?1 AND NOT isDeleted AND isPublished = ?2", authorId, published);
        }

        @Override
        public @Nonnull Uni<Integer> softDeleteById(@Nonnull UUID id) {
            return update("isDeleted = TRUE, life = 0 WHERE id = ?1", id);
        }

        public @Nonnull Uni<Integer> softDeleteByAuthorId(@Nonnull UUID authorId) {
            return update("isDeleted = TRUE, life = 0 WHERE author.id = ?1 AND NOT isDeleted", authorId);
        }

        public @Nonnull Uni<Integer> restoreById(
                @Nonnull UUID id,
                boolean isDeleted,
                int life,
                boolean isPublished,
                boolean anonymous,
                @Nonnull OffsetDateTime dateCreated) {
            return update(
                    "isDeleted = ?2, life = ?3, isPublished = ?4, anonymous = ?5, dateCreated = ?6 WHERE id = ?1",
                    id,
                    isDeleted,
                    life,
                    isPublished,
                    anonymous,
                    dateCreated);
        }

        public @Nonnull Uni<Integer> updatePublishedById(@Nonnull UUID postId, boolean anonymous) {
            return update(
                    "dateCreated = CURRENT_TIMESTAMP, isPublished = TRUE, life = ?2, anonymous = ?3 WHERE id = ?1",
                    postId,
                    INITIAL_LIFE,
                    anonymous);
        }
    }
}
