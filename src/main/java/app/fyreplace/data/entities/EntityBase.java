package app.fyreplace.data.entities;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.reactive.mutiny.Mutiny;

@Getter
@Setter
@MappedSuperclass
public abstract class EntityBase {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    public UUID id;

    public interface RepositoryBase<E extends EntityBase> extends PanacheRepositoryBase<E, UUID> {
        default @Nonnull Uni<Long> deleteByIdNotIn(@Nonnull Collection<UUID> ids) {
            return delete("id NOT IN ?1", ids);
        }

        default @Nonnull Uni<Void> executeNative(@Nonnull String sql, @Nonnull Map<String, ?> parameters) {
            return nativeQuery(sql, parameters)
                    .call(Mutiny.Query::executeUpdate)
                    .replaceWithVoid();
        }

        default @Nonnull Uni<Mutiny.Query<?>> nativeQuery(@Nonnull String sql, @Nonnull Map<String, ?> parameters) {
            return getSession().map(session -> createNativeQuery(session, sql, parameters));
        }

        default <T> @Nonnull Uni<Mutiny.SelectionQuery<T>> nativeQuery(
                @Nonnull Class<T> type, @Nonnull String sql, @Nonnull Map<String, ?> parameters) {
            return getSession().map(session -> createNativeQuery(session, type, sql, parameters));
        }

        private static @Nonnull Mutiny.Query<?> createNativeQuery(
                @Nonnull Mutiny.Session session, @Nonnull String sql, @Nonnull Map<String, ?> parameters) {
            final var query = session.createNativeQuery(sql);
            parameters.forEach(query::setParameter);
            return query;
        }

        private static <T> @Nonnull Mutiny.SelectionQuery<T> createNativeQuery(
                @Nonnull Mutiny.Session session,
                @Nonnull Class<T> type,
                @Nonnull String sql,
                @Nonnull Map<String, ?> parameters) {
            final var query = session.createNativeQuery(sql, type);
            parameters.forEach(query::setParameter);
            return query;
        }
    }
}
