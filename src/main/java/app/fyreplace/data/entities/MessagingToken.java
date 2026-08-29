package app.fyreplace.data.entities;

import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "messaging_tokens")
public final class MessagingToken extends EntityBase {
    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public User user;

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    public Service service;

    @Column(nullable = false, unique = true)
    public String token;

    public enum Service {
        APNS,
        FCM,
        WEB,
        WNS
    }

    @ApplicationScoped
    public static final class Repository implements RepositoryBase<MessagingToken> {
        public @Nonnull PanacheQuery<MessagingToken> findByUserId(@Nonnull UUID userId) {
            return find("user.id", userId);
        }
    }
}
