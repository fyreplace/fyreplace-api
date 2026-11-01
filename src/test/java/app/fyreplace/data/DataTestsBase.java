package app.fyreplace.data;

import app.fyreplace.TestsBase;
import app.fyreplace.data.entities.Block;
import app.fyreplace.data.entities.Chapter;
import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.MessagingToken;
import app.fyreplace.data.entities.Notification;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.Report;
import app.fyreplace.data.entities.Subscription;
import app.fyreplace.data.entities.User;
import app.fyreplace.data.entities.Vote;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import io.vertx.sqlclient.DatabaseException;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.reactive.mutiny.Mutiny;

public abstract class DataTestsBase extends TestsBase {
    @Inject
    protected User.Repository userRepository;

    @Inject
    protected Block.Repository blockRepository;

    @Inject
    protected Post.Repository postRepository;

    @Inject
    protected Chapter.Repository chapterRepository;

    @Inject
    protected Subscription.Repository subscriptionRepository;

    @Inject
    protected Comment.Repository commentRepository;

    @Inject
    protected Notification.Repository notificationRepository;

    @Inject
    protected Vote.Repository voteRepository;

    @Inject
    protected MessagingToken.Repository messagingTokenRepository;

    @Inject
    protected Report.Repository reportRepository;

    protected static <E> @Nonnull Uni<E> refresh(@Nonnull E entity) {
        return Panache.getSession()
                .call(Mutiny.Session::flush)
                .flatMap(session -> session.refresh(entity))
                .replaceWith(entity);
    }

    protected static boolean isUniqueViolation(@Nonnull Throwable failure) {
        for (var cursor = failure; cursor != null && cursor != cursor.getCause(); cursor = cursor.getCause()) {
            if (cursor instanceof ConstraintViolationException) {
                return true;
            }

            if (cursor instanceof DatabaseException db && "23505".equals(db.getSqlState())) {
                return true;
            }

            if (String.valueOf(cursor.getMessage()).contains("duplicate key value")) {
                return true;
            }
        }

        return false;
    }
}
