package app.fyreplace.data.entities;

import app.fyreplace.errors.UnauthenticatedException;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
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
@Table(name = "users")
@NamedQueries({@NamedQuery(name = User.Q_FIND_BLOCKED_BY, query = """
    FROM User AS users
    INNER JOIN Block ON users.id = target.id
    WHERE issuer.id = :issuerId
    """)})
public final class User extends SoftDeletableEntityBase {
    public static final String Q_FIND_BLOCKED_BY = "User.findBlockedBy";
    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 50;
    public static final int EMAIL_MIN_LENGTH = 5;
    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int BIO_MAX_LENGTH = 3000;
    public static final Duration LIFETIME = Duration.ofDays(1);

    @Column(length = USERNAME_MAX_LENGTH, unique = true)
    public @Nullable String username;

    @Column(nullable = false)
    public String password;

    @Column(length = EMAIL_MAX_LENGTH, unique = true)
    public @Nullable String email;

    @Column(nullable = false)
    public boolean active = false;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    public Rank rank = Rank.CITIZEN;

    @OneToOne(cascade = CascadeType.PERSIST)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    public @Nullable StoredFile avatar;

    @Column(length = BIO_MAX_LENGTH, nullable = false)
    public String bio = "";

    @Column(nullable = false)
    @Enumerated(value = EnumType.ORDINAL)
    public BanCount banCount = BanCount.NEVER;

    public @Nullable OffsetDateTime dateBanEnd;

    public boolean isPending() {
        return isAlive() && !active;
    }

    public boolean isAlive() {
        return !isDeleted() && !isBanned();
    }

    public boolean isBanned() {
        return dateBanEnd != null || banCount == BanCount.ONE_TOO_MANY;
    }

    public boolean hasUsablePassword() {
        return !password.isEmpty();
    }

    public enum Rank {
        CITIZEN,
        STAFF,
        ADMIN
    }

    public enum BanCount {
        NEVER,
        ONCE,
        ONE_TOO_MANY
    }

    @ApplicationScoped
    public static final class Repository implements SoftDeleteRepositoryBase<User> {
        @Inject
        StoredFile.Repository storedFileRepository;

        public @Nonnull Uni<User> findByUsername(@Nonnull String username) {
            return find("username", username).singleResult();
        }

        public @Nonnull Uni<User> findByEmail(@Nonnull String email) {
            return find("email", email).singleResult();
        }

        public @Nonnull Uni<User> findExistingById(@Nonnull UUID id) {
            return find("id = ?1 AND NOT isDeleted", id).singleResult();
        }

        public @Nonnull Uni<Boolean> existsByUsername(@Nonnull String username) {
            return count("username", username).map(count -> count > 0);
        }

        public @Nonnull Uni<Boolean> existsByEmail(@Nonnull String email) {
            return count("email", email).map(count -> count > 0);
        }

        public @Nonnull PanacheQuery<User> findBlockedBy(@Nonnull UUID issuerId) {
            return find("#" + User.Q_FIND_BLOCKED_BY, Map.of("issuerId", issuerId));
        }

        public @Nonnull Uni<Integer> updateBioByUserId(@Nonnull UUID userId, @Nonnull String bio) {
            return update("bio = ?2 WHERE id = ?1", userId, bio);
        }

        public @Nonnull Uni<Integer> updateActiveById(@Nonnull UUID userId, boolean active) {
            return update("active = ?2 WHERE id = ?1", userId, active);
        }

        public @Nonnull Uni<Integer> updateDateCreatedById(@Nonnull UUID userId, @Nonnull OffsetDateTime dateCreated) {
            return update("dateCreated = ?2 WHERE id = ?1", userId, dateCreated);
        }

        public @Nonnull Uni<Integer> updateEmailById(@Nonnull UUID userId, @Nonnull String email) {
            return update("email = ?2 WHERE id = ?1", userId, email);
        }

        public @Nonnull Uni<Long> deleteByDateCreatedAfter(@Nonnull OffsetDateTime threshold) {
            return delete("dateCreated > ?1", threshold);
        }

        public @Nonnull Uni<Long> deleteExpiredPending() {
            return delete(
                    "active = FALSE AND NOT isDeleted AND dateCreated <= ?1",
                    OffsetDateTime.now().minus(LIFETIME));
        }

        @Override
        public @Nonnull Uni<Integer> softDeleteById(@Nonnull UUID id) {
            return findById(id)
                    .onItem()
                    .ifNull()
                    .failWith(() -> new UnauthenticatedException("invalid_token"))
                    .call(this::wipe)
                    .call(this::removeAvatar)
                    .replaceWith(1);
        }

        public @Nonnull Uni<Integer> restoreByUsername(@Nonnull String username) {
            return update("isDeleted = FALSE WHERE username = ?1", username);
        }

        public @Nonnull Uni<Integer> updateBanByUserId(@Nonnull UUID userId, int days) {
            return nativeQuery(
                            User.class,
                            "SELECT * FROM users__ban(:userId, :banEnd)",
                            Map.of(
                                    "userId",
                                    userId,
                                    "banEnd",
                                    OffsetDateTime.now().plusDays(days)))
                    .flatMap(Mutiny.SelectionQuery::getResultList)
                    .map(List::size);
        }

        public @Nonnull Uni<Integer> resetBanByUsername(@Nonnull String username) {
            return update("banCount = ?1, dateBanEnd = NULL WHERE username = ?2", BanCount.NEVER, username);
        }

        public @Nonnull Uni<Integer> updateRankByUserId(@Nonnull UUID userId, @Nonnull Rank rank) {
            return update("rank = ?2 WHERE id = ?1", userId, rank);
        }

        public @Nonnull Uni<Integer> updateRankByUsername(@Nonnull String username, @Nonnull Rank rank) {
            return update("rank = ?2 WHERE username = ?1", username, rank);
        }

        public @Nonnull Uni<Integer> resetBioByUsername(@Nonnull String username, @Nonnull String bio) {
            return update("bio = ?2 WHERE username = ?1", username, bio);
        }

        public @Nonnull Uni<Integer> resetEmailByUsername(@Nonnull String username, @Nonnull String email) {
            return update("email = ?2 WHERE username = ?1", username, email);
        }

        public @Nonnull Uni<Integer> resetActiveByUsername(@Nonnull String username) {
            return update("active = TRUE WHERE username = ?1", username);
        }

        public @Nonnull Uni<StoredFile> storeOrRemoveAvatarById(@Nonnull UUID id, @Nonnull byte[] data) {
            return findById(id)
                    .call(this::removeAvatar)
                    .flatMap(user -> data.length > 0 ? storeAvatar(user, data) : clearAvatar(user));
        }

        private @Nonnull Uni<Void> wipe(@Nonnull User user) {
            return update(
                            "isDeleted = TRUE, username = NULL, email = NULL, password = '', bio = '', avatar = NULL WHERE id = ?1",
                            user.getId())
                    .replaceWithVoid();
        }

        private @Nonnull Uni<Void> removeAvatar(@Nonnull User user) {
            return user.getAvatar() == null
                    ? Uni.createFrom().voidItem()
                    : storedFileRepository.removeByPath(user.getAvatar().getPath());
        }

        private @Nonnull Uni<StoredFile> storeAvatar(@Nonnull User user, @Nonnull byte[] data) {
            return storedFileRepository
                    .store("avatars", data)
                    .flatMap(file -> update("avatar = ?2 WHERE id = ?1", user.getId(), file)
                            .replaceWith(file));
        }

        private @Nonnull Uni<StoredFile> clearAvatar(@Nonnull User user) {
            return update("avatar = NULL WHERE id = ?1", user.getId()).replaceWith((StoredFile) null);
        }
    }
}
