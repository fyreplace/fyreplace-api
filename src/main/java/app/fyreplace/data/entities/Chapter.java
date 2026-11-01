package app.fyreplace.data.entities;

import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
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
public final class Chapter extends EntityBase {
    public static final int TEXT_MAX_LENGTH = 500;

    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public Post post;

    @Column(nullable = false)
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

        public @Nonnull Uni<Long> countByPostId(@Nonnull UUID postId) {
            return count("post.id", postId);
        }

        public @Nonnull Uni<Long> countEmptyByPostId(@Nonnull UUID postId) {
            return count("post.id = ?1 AND text = '' AND image IS NULL", postId);
        }

        public @Nonnull Uni<Void> updateTextByLocation(@Nonnull Location location, @Nonnull String text) {
            return findByLocation(location)
                    .singleResult()
                    .call(this::removeImage)
                    .call(chapter -> update(
                            "text = ?2, image = NULL, width = 0, height = 0 WHERE id = ?1", chapter.getId(), text))
                    .replaceWithVoid();
        }

        public @Nonnull Uni<StoredFile> storeOrRemoveImageByLocationOrNull(
                @Nonnull Location location, @Nonnull byte[] data, int width, int height) {
            return findByLocation(location)
                    .singleResult()
                    .call(this::removeImage)
                    .chain(() -> data.length > 0 ? storeImage(location, data, width, height) : clearImage(location));
        }

        public @Nonnull Uni<Integer> restoreById(
                @Nonnull UUID id,
                int position,
                @Nonnull String text,
                @Nullable StoredFile image,
                int width,
                int height) {
            return update(
                    "position = ?2, text = ?3, image = ?4, width = ?5, height = ?6 WHERE id = ?1",
                    id,
                    position,
                    text,
                    image,
                    width,
                    height);
        }

        public @Nonnull Uni<Void> moveByLocation(@Nonnull Location location, int newPosition) {
            return executeNative(
                    "CALL chapters__move(:postId, :before, :after)",
                    Map.of(
                            "postId", location.postId(),
                            "before", location.position(),
                            "after", newPosition));
        }

        public @Nonnull Uni<Void> deleteByLocation(@Nonnull Location location) {
            return findByLocation(location)
                    .singleResult()
                    .call(this::removeImage)
                    .call(this::delete)
                    .replaceWithVoid();
        }

        public @Nonnull Uni<Void> deleteByPostId(@Nonnull UUID postId) {
            return deleteByPostQuery("post.id = ?1", postId);
        }

        public @Nonnull Uni<Void> deleteByPostAuthorId(@Nonnull UUID authorId) {
            return deleteByPostQuery("post.author.id = ?1", authorId);
        }

        private @Nonnull Uni<Void> deleteByPostQuery(@Nonnull String query, @Nonnull UUID param) {
            return find(query, param)
                    .list()
                    .flatMap(chapters -> Multi.createFrom()
                            .iterable(chapters)
                            .onItem()
                            .transformToUni(this::removeImage)
                            .concatenate()
                            .collect()
                            .asList())
                    .chain(() -> delete(query, param))
                    .replaceWithVoid();
        }

        private @Nonnull Uni<Void> removeImage(@Nonnull Chapter chapter) {
            return chapter.getImage() == null
                    ? Uni.createFrom().voidItem()
                    : storedFileRepository.removeByPath(chapter.getImage().getPath());
        }

        private @Nonnull Uni<StoredFile> storeImage(
                @Nonnull Location location, @Nonnull byte[] data, int width, int height) {
            return storedFileRepository
                    .store("chapters", data)
                    .flatMap(file -> update(
                                    "text = '', image = ?3, width = ?4, height = ?5 WHERE post.id = ?1 AND position = ?2",
                                    location.postId(),
                                    location.position(),
                                    file,
                                    width,
                                    height)
                            .replaceWith(file));
        }

        private @Nonnull Uni<StoredFile> clearImage(@Nonnull Location location) {
            return update(
                            "text = '', image = NULL, width = 0, height = 0 WHERE post.id = ?1 AND position = ?2",
                            location.postId(),
                            location.position())
                    .replaceWith((StoredFile) null);
        }
    }
}
