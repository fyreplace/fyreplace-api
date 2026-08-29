package app.fyreplace.data.entities;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.quarkus.elytron.security.common.BcryptUtil;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public static final Set<String> FORBIDDEN_USERNAMES = new HashSet<>(Arrays.asList(
            "admin",
            "admins",
            "administrator",
            "administrators",
            "anonymous",
            "author",
            "authors",
            "fyreplace",
            "fyreplaces",
            "guest",
            "guests",
            "management",
            "managements",
            "manager",
            "managers",
            "mod",
            "mods",
            "moderator",
            "moderators",
            "nil",
            "none",
            "nul",
            "null",
            "root",
            "roots",
            "superuser",
            "superusers",
            "sysadmin",
            "sysadmins",
            "system",
            "systems",
            "systemadmin",
            "systemadmins",
            "systemadministrator",
            "systemadministrators",
            "systemsadmin",
            "systemsadmins",
            "systemsadministrator",
            "systemsadministrators",
            "user",
            "users",
            "username",
            "usernames",
            "void",
            "voids"));
    public static final int USERNAME_MIN_LENGTH = 3;
    public static final int USERNAME_MAX_LENGTH = 50;
    public static final int EMAIL_MIN_LENGTH = 5;
    public static final int EMAIL_MAX_LENGTH = 254;
    public static final String USERNAME_PATTERN = "^[\\w.@+-]+$";
    public static final int BIO_MAX_LENGTH = 3000;
    public static Duration LIFETIME = Duration.ofDays(1);

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

    public void validatePassword(@Nonnull String plainText) throws StatusRuntimeException {
        if (password == null || !BcryptUtil.matches(plainText, password)) {
            throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
        }
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

        public @Nonnull PanacheQuery<User> findBlockedBy(@Nonnull UUID issuerId) {
            return find("#" + User.Q_FIND_BLOCKED_BY, Map.of("issuerId", issuerId));
        }

        public @Nonnull Uni<Integer> updateBioByUserId(@Nonnull UUID userId, @Nonnull String bio) {
            return update("bio = ?2 WHERE id = ?1", userId, bio);
        }

        public @Nonnull Uni<Integer> updateBanByUserId(@Nonnull UUID userId, int days) {
            return getSession()
                    .map(session -> session.createNativeQuery("SELECT * FROM users__ban(:userId, :banEnd)", User.class)
                            .setParameter("userId", userId)
                            .setParameter("banEnd", OffsetDateTime.now().plusDays(days)))
                    .flatMap(Mutiny.SelectionQuery::getResultList)
                    .map(List::size);
        }

        public @Nonnull Uni<Integer> resetBanByUsername(@Nonnull String username) {
            return update("banCount = ?1, dateBanEnd = NULL WHERE username = ?2", BanCount.NEVER, username);
        }

        public @Nonnull Uni<Integer> updateRankByUserId(@Nonnull UUID userId, @Nonnull Rank rank) {
            return update("rank = ?2 WHERE id = ?1", userId, rank);
        }

        public @Nonnull Uni<StoredFile> storeOrRemoveAvatarById(@Nonnull UUID id, @Nonnull byte[] data) {
            if (data.length > 0) {
                return storedFileRepository
                        .store("avatars", data)
                        .call(file -> update("avatar = ?2 WHERE id = ?1", id, file));
            } else {
                return update("avatar = NULL WHERE id = ?1", id)
                        .call(() -> findById(id).map(User::getAvatar).flatMap(storedFileRepository::remove))
                        .replaceWith((StoredFile) null);
            }
        }
    }
}
