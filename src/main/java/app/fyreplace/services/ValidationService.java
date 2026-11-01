package app.fyreplace.services;

import app.fyreplace.data.entities.Chapter;
import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.MessagingToken;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.User;
import app.fyreplace.errors.AlreadyExistsException;
import app.fyreplace.errors.ApiException;
import app.fyreplace.errors.InvalidArgumentException;
import app.fyreplace.errors.PermissionDeniedException;
import app.fyreplace.protos.MessagingService;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.text.Normalizer;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public final class ValidationService {
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_.!#$%&'*+/=?^`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?"
                    + "(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+$");
    private static final Set<String> RESERVED_USERNAMES = Set.of(
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
                    "user",
                    "users",
                    "username",
                    "usernames",
                    "void",
                    "voids")
            .stream()
            .map(ValidationService::normalizeUsername)
            .collect(Collectors.toUnmodifiableSet());

    @Inject
    Chapter.Repository chapterRepository;

    @Inject
    User.Repository userRepository;

    @Inject
    ImageService imageService;

    public @Nonnull Uni<Void> checkUsernameReserved(@Nonnull String username) {
        return failUnless(
                !RESERVED_USERNAMES.contains(normalizeUsername(username)),
                new PermissionDeniedException("username_reserved"));
    }

    public @Nonnull Uni<Void> checkUsernameAvailable(@Nonnull String username) {
        return userRepository
                .existsByUsername(username)
                .flatMap(taken -> failUnless(!taken, new AlreadyExistsException("username_taken")));
    }

    public @Nonnull Uni<Void> checkEmailAvailable(@Nonnull String email) {
        return userRepository
                .existsByEmail(email)
                .flatMap(taken -> failUnless(!taken, new AlreadyExistsException("email_taken")));
    }

    public @Nonnull Uni<Void> checkUsername(@Nonnull String username) {
        return failUnless(
                username.length() >= User.USERNAME_MIN_LENGTH && username.length() <= User.USERNAME_MAX_LENGTH,
                new InvalidArgumentException("invalid_username"));
    }

    public @Nonnull Uni<Void> checkEmail(@Nonnull String email) {
        return failUnless(
                email.length() >= User.EMAIL_MIN_LENGTH
                        && email.length() <= User.EMAIL_MAX_LENGTH
                        && EMAIL_PATTERN.matcher(email).matches(),
                new InvalidArgumentException("invalid_email"));
    }

    public @Nonnull Uni<Void> checkPassword(@Nonnull User user, @Nonnull String plainText) {
        return failUnless(
                user.hasUsablePassword() && BcryptUtil.matches(plainText, user.password),
                new InvalidArgumentException("invalid_password"));
    }

    public @Nonnull Uni<Void> checkBio(@Nonnull String bio) {
        return failUnless(bio.length() <= User.BIO_MAX_LENGTH, new InvalidArgumentException("invalid_bio"));
    }

    public @Nonnull Uni<Void> checkEmailUpdate(@Nonnull String email) {
        return checkEmail(email).call(() -> checkEmailAvailable(email));
    }

    public @Nonnull Uni<Void> checkDays(int days) {
        return failUnless(days > 0, new InvalidArgumentException("invalid_days"));
    }

    public @Nonnull Uni<Void> checkImage(@Nonnull byte[] data) {
        return failUnless(
                data.length == 0 || imageService.isValidImage(data), new InvalidArgumentException("invalid_image"));
    }

    public @Nonnull Uni<Void> checkChapterText(@Nonnull String text) {
        return failUnless(text.length() <= Chapter.TEXT_MAX_LENGTH, new InvalidArgumentException("invalid_text"));
    }

    public @Nonnull Uni<Void> checkCommentText(@Nonnull String text) {
        return failUnless(!text.isEmpty(), new InvalidArgumentException("comment_empty"))
                .call(() -> failUnless(
                        text.length() <= Comment.TEXT_MAX_LENGTH, new InvalidArgumentException("invalid_text")));
    }

    public @Nonnull Uni<Void> checkMessagingToken(@Nonnull app.fyreplace.protos.MessagingToken messagingToken) {
        return failUnless(
                        messagingToken.getService() == MessagingService.MESSAGING_SERVICE_APNS
                                || messagingToken.getService() == MessagingService.MESSAGING_SERVICE_FCM,
                        new InvalidArgumentException("invalid_service"))
                .call(() -> failUnless(
                        !messagingToken.getToken().isEmpty()
                                && messagingToken.getToken().length() <= MessagingToken.TOKEN_MAX_LENGTH,
                        new InvalidArgumentException("invalid_token")));
    }

    public @Nonnull Uni<Void> checkChapterInsertable(int position, @Nonnull UUID postId) {
        return chapterRepository
                .countByPostId(postId)
                .flatMap(count -> failUnless(position <= count, new InvalidArgumentException("invalid_position"))
                        .call(() -> failUnless(
                                count < Post.MAX_CHAPTERS, new PermissionDeniedException("too_many_chapters"))));
    }

    public @Nonnull Uni<Void> checkChapterPosition(int position, @Nonnull UUID postId) {
        return chapterRepository
                .countByPostId(postId)
                .flatMap(count -> failUnless(position < count, new InvalidArgumentException("invalid_position")));
    }

    public @Nonnull Uni<Void> checkChapterRelocation(int fromPosition, int toPosition, @Nonnull UUID postId) {
        return chapterRepository
                .countByPostId(postId)
                .flatMap(count -> failUnless(
                        fromPosition < count && (toPosition <= fromPosition || toPosition < count),
                        new InvalidArgumentException("invalid_position")));
    }

    public @Nonnull Uni<Void> checkPostPublishable(@Nonnull UUID postId) {
        return chapterRepository
                .countByPostId(postId)
                .flatMap(count -> failUnless(count > 0, new InvalidArgumentException("post_empty")))
                .call(() -> chapterRepository
                        .countEmptyByPostId(postId)
                        .flatMap(emptyCount ->
                                failUnless(emptyCount == 0, new InvalidArgumentException("chapter_empty"))));
    }

    private static @Nonnull Uni<Void> failUnless(boolean condition, @Nonnull ApiException failure) {
        return condition ? Uni.createFrom().voidItem() : Uni.createFrom().failure(failure);
    }

    private static @Nonnull String normalizeUsername(@Nonnull String username) {
        return Normalizer.normalize(username, Normalizer.Form.NFKD)
                .chars()
                .filter(c -> c < 0x80 && (Character.isLetterOrDigit(c) || c == '_'))
                .collect(StringBuilder::new, (builder, c) -> builder.append((char) c), StringBuilder::append)
                .toString()
                .toUpperCase();
    }
}
