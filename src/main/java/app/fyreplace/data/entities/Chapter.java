package app.fyreplace.data.entities;

import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;
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
@Table(
        name = "chapters",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"post_id", "position"})})
@NamedQueries({
    @NamedQuery(name = Chapter.Q_DELETE_BY_LOCATION, query = """
        DELETE FROM Chapter
        WHERE id = (
            SELECT id FROM Chapter
            WHERE post.id = :postId
            ORDER BY position
            LIMIT 1 OFFSET :position
        )
        """),
    @NamedQuery(name = Chapter.Q_UPDATE_TEXT_BY_LOCATION, query = """
        UPDATE Chapter
        SET text = :text
        WHERE id = (
            SELECT id FROM Chapter
            WHERE post.id = :postId
            ORDER BY position
            LIMIT 1 OFFSET :position
        )
        """)
})
public final class Chapter extends EntityBase {
    public static final String Q_DELETE_BY_LOCATION = "Chapter.deleteByLocation";
    public static final String Q_UPDATE_TEXT_BY_LOCATION = "Chapter.updateTextByLocation";
    public static final int TEXT_MAX_LENGTH = 500;

    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public Post post;

    @Column(length = 50, nullable = false)
    public int position;

    @Column(length = TEXT_MAX_LENGTH, nullable = false)
    public String text = "";

    @OneToOne(cascade = CascadeType.PERSIST)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    public StoredFile image;

    @Column(nullable = false)
    public int width = 0;

    @Column(nullable = false)
    public int height = 0;

    public record Location(@Nonnull UUID postId, @Nonnull Integer position) {}

    @ApplicationScoped
    public static final class Repository implements RepositoryBase<Chapter> {
        @Inject
        StoredFile.Repository storedFileRepository;

        public @Nonnull PanacheQuery<Chapter> findByPostId(@Nonnull UUID postId) {
            return find("post.id = ?1 ORDER BY position", postId);
        }

        public @Nonnull PanacheQuery<Chapter> findByLocation(@Nonnull Location location) {
            return find("post.id = ?1 ORDER BY position", location.postId()).page(location.position(), 1);
        }

        public @Nonnull Uni<Integer> updateTextByLocation(@Nonnull Location location, @Nonnull String text) {
            return update(
                    "#" + Q_UPDATE_TEXT_BY_LOCATION,
                    Map.of("postId", location.postId(), "position", location.position(), "text", text));
        }

        public @Nonnull Uni<StoredFile> storeOrRemoveImageByLocation(@Nonnull Location location, @Nonnull byte[] data) {
            if (data.length > 0) {
                return storedFileRepository
                        .store("chapters", data)
                        .call(file -> update(
                                "image = ?3 WHERE post.id = ?1 AND position = ?2",
                                location.postId(),
                                location.position(),
                                file));
            } else {
                return update(
                                "image = NULL WHERE post.id = ?1 AND position = ?2",
                                location.postId(),
                                location.position())
                        .call(() -> findByLocation(location)
                                .singleResult()
                                .map(Chapter::getImage)
                                .flatMap(storedFileRepository::remove))
                        .replaceWith((StoredFile) null);
            }
        }

        public @Nonnull Uni<Void> moveByLocation(@Nonnull Location location, int newPosition) {
            return getSession()
                    .call(s -> s.createNativeQuery("CALL chapters__move(:postId, :before, :after)")
                            .setParameter("postId", location.postId())
                            .setParameter("before", location.position())
                            .setParameter("after", newPosition)
                            .executeUpdate())
                    .replaceWithVoid();
        }

        public @Nonnull Uni<Long> deleteByLocation(@Nonnull Location location) {
            return delete(
                    "#" + Q_DELETE_BY_LOCATION, Map.of("postId", location.postId(), "position", location.position()));
        }
    }
}
