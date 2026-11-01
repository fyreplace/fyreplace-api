package app.fyreplace.services;

import app.fyreplace.data.entities.Block;
import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.User;
import app.fyreplace.errors.CancelledException;
import app.fyreplace.errors.InvalidArgumentException;
import app.fyreplace.errors.PermissionDeniedException;
import app.fyreplace.errors.UnauthenticatedException;
import app.fyreplace.protos.Rank;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonNumber;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
public final class AuthorizationService {
    public static final Duration EMAIL_TOKEN_LIFE = Duration.ofDays(1);

    @Inject
    Block.Repository blockRepository;

    @Inject
    User.Repository userRepository;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    JwtService jwtService;

    public void checkUser(@Nonnull User user) {
        final var detail = switch (user) {
            case User u when u.isPending() -> "caller_pending";
            case User u when u.isDeleted() -> "caller_deleted";
            case User u when u.isBanned() && u.getDateBanEnd() == null -> "caller_banned_permanently";
            case User u when u.isBanned() -> "caller_banned";
            default -> null;
        };

        if (detail != null) {
            throw new PermissionDeniedException(detail);
        }
    }

    public void checkPending(@Nonnull User user) {
        if (!user.isPending()) {
            throw new PermissionDeniedException("user_not_pending");
        }
    }

    public void checkPasswordless(@Nonnull User user) {
        if (user.hasUsablePassword()) {
            throw new CancelledException("caller_has_password");
        }
    }

    public void checkNotSelf(@Nonnull UUID targetId) {
        if (targetId.equals(currentUserService.getUserId())) {
            throw new PermissionDeniedException("user_is_caller");
        }
    }

    public void checkReportable(@Nonnull User caller, @Nonnull User target) {
        if (target.getRank().compareTo(caller.getRank()) > 0) {
            throw new PermissionDeniedException("caller_rank_insufficient");
        }
    }

    public void checkRankSufficient(@Nonnull User caller, @Nonnull User target) {
        if (target.getRank().compareTo(caller.getRank()) >= 0) {
            throw new PermissionDeniedException("caller_rank_insufficient");
        }
    }

    public void checkSuperuser(@Nonnull User caller) {
        if (caller.getRank() != User.Rank.ADMIN) {
            throw new PermissionDeniedException("caller_rank_insufficient");
        }
    }

    public void checkStaff(@Nonnull User caller) {
        if (caller.getRank().compareTo(User.Rank.STAFF) < 0) {
            throw new PermissionDeniedException("caller_rank_insufficient");
        }
    }

    public void checkPostWritable(@Nonnull UUID callerId, @Nonnull Post post) {
        if (post.isPublished()) {
            throw new PermissionDeniedException("post_published");
        }

        if (!post.getAuthor().getId().equals(callerId)) {
            throw new PermissionDeniedException("invalid_post_author");
        }
    }

    public void checkPostPublished(@Nonnull Post post) {
        if (!post.isPublished()) {
            throw new PermissionDeniedException("post_not_published");
        }
    }

    public void checkPostAuthorOrStaff(@Nonnull User caller, @Nonnull Post post) {
        if (!post.getAuthor().getId().equals(caller.getId()) && caller.getRank().compareTo(User.Rank.STAFF) < 0) {
            throw new PermissionDeniedException("invalid_post_author");
        }
    }

    public void checkPostReportable(@Nonnull UUID callerId, @Nonnull Post post) {
        if (post.getAuthor().getId().equals(callerId)) {
            throw new PermissionDeniedException("invalid_post");
        }
    }

    public void checkPostAbsolvable(@Nonnull UUID callerId, @Nonnull Post post) {
        if (post.getAuthor().getId().equals(callerId)) {
            throw new PermissionDeniedException("caller_owns_post");
        }
    }

    public void checkCommentAuthorOrStaff(@Nonnull User caller, @Nonnull Comment comment) {
        if (!comment.getAuthor().getId().equals(caller.getId())
                && caller.getRank().compareTo(User.Rank.STAFF) < 0) {
            throw new PermissionDeniedException("invalid_comment_author");
        }
    }

    public void checkCommentReportable(@Nonnull UUID callerId, @Nonnull Comment comment) {
        if (comment.getAuthor().getId().equals(callerId)) {
            throw new PermissionDeniedException("invalid_comment");
        }

        if (comment.isDeleted()) {
            throw new PermissionDeniedException("comment_deleted");
        }
    }

    public void checkCommentAbsolvable(@Nonnull UUID callerId, @Nonnull Comment comment) {
        if (comment.getAuthor().getId().equals(callerId)) {
            throw new PermissionDeniedException("caller_owns_comment");
        }
    }

    public void checkTargetActive(@Nonnull User target) {
        if (!target.active) {
            throw new PermissionDeniedException("user_not_active");
        }
    }

    public void checkTargetNotBanned(@Nonnull User target) {
        if (target.isBanned()) {
            throw new PermissionDeniedException("user_banned");
        }
    }

    public void checkPromotionRank(@Nonnull Rank rank, @Nonnull User target) {
        if (rank == Rank.RANK_UNSPECIFIED || rank == Rank.UNRECOGNIZED) {
            throw new InvalidArgumentException("rank_unspecified");
        }

        if (rank == Rank.RANK_SUPERUSER && target.getRank() == User.Rank.CITIZEN) {
            throw new PermissionDeniedException("unsupported_promotion");
        }
    }

    public void checkEmailUpdateUser(@Nonnull User tokenUser, @Nonnull User caller) {
        if (!tokenUser.getId().equals(caller.getId())) {
            throw new PermissionDeniedException("invalid_user");
        }
    }

    public @Nonnull Uni<Void> checkNotBlockedBy(@Nonnull UUID authorId, @Nonnull UUID callerId) {
        return blockRepository
                .isBlocking(authorId, callerId)
                .flatMap(blocked -> blocked
                        ? Uni.createFrom().failure(new PermissionDeniedException("caller_blocked"))
                        : Uni.createFrom().voidItem());
    }

    @WithSession
    public @Nonnull Uni<User> connectUser(@Nonnull String email) {
        return userRepository.findByEmail(email);
    }

    @WithSession
    public @Nonnull Uni<User> emailTokenUser(@Nonnull String token) {
        return Uni.createFrom()
                .item(token)
                .map(this::verifiedClaims)
                .map(this::userIdOf)
                .flatMap(userRepository::findById)
                .onItem()
                .ifNull()
                .failWith(() -> new UnauthenticatedException("invalid_token"));
    }

    public @Nonnull Uni<EmailUpdate> emailUpdate(@Nonnull String token) {
        return Uni.createFrom()
                .item(token)
                .map(this::verifiedClaims)
                .flatMap(claims -> userRepository
                        .findById(userIdOf(claims))
                        .onItem()
                        .ifNull()
                        .failWith(() -> new UnauthenticatedException("invalid_token"))
                        .map(user -> new EmailUpdate(user, emailClaimOf(claims))));
    }

    private @Nonnull JsonWebToken verifiedClaims(@Nonnull String token) {
        final var parsed = jwtService.verifyToken(token);

        if (parsed == null) {
            throw new UnauthenticatedException("invalid_token");
        }

        return parsed;
    }

    private @Nonnull UUID userIdOf(@Nonnull JsonWebToken claims) {
        final var userId = claims.<String>claim("user_id").orElse(null);

        if (userId == null) {
            throw new UnauthenticatedException("invalid_token");
        }

        final var timestampClaim = claims.claim("timestamp").orElse(null);

        if (!(timestampClaim instanceof JsonNumber jsonNumber)) {
            throw new UnauthenticatedException("invalid_token");
        }

        if (Instant.now().getEpochSecond() - jsonNumber.longValue() > EMAIL_TOKEN_LIFE.toSeconds()) {
            throw new UnauthenticatedException("timestamp_exceeded");
        }

        return UUID.fromString(userId);
    }

    private @Nonnull String emailClaimOf(@Nonnull JsonWebToken claims) {
        return claims.<String>claim("email").orElseThrow(() -> new UnauthenticatedException("invalid_token"));
    }

    public record EmailUpdate(@Nonnull User user, @Nonnull String email) {}
}
