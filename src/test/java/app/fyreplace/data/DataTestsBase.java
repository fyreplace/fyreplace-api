package app.fyreplace.data;

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
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.smallrye.mutiny.tuples.Tuple3;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.DatabaseException;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.reactive.mutiny.Mutiny;

public abstract class DataTestsBase {
    @Inject
    protected Vertx vertx;

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

    protected static <A, B> @Nonnull Uni<Tuple2<A, B>> joinAsTuple(@Nonnull Uni<A> aUni, @Nonnull Uni<B> bUni) {
        return aUni.flatMap(a -> bUni.map(b -> Tuple2.of(a, b)));
    }

    protected static <A, B, C> @Nonnull Uni<Tuple3<A, B, C>> joinAsTuple(
            @Nonnull Uni<A> aUni, @Nonnull Uni<B> bUni, @Nonnull Uni<C> cUni) {
        return aUni.flatMap(a -> bUni.flatMap(b -> cUni.map(c -> Tuple3.of(a, b, c))));
    }

    protected <E> void awaitOnSafeContext(@Nonnull Supplier<Uni<E>> action) {
        final var done = new CompletableFuture<Void>();
        final var context = VertxContext.getOrCreateDuplicatedContext(vertx.getOrCreateContext());

        VertxContextSafetyToggle.setContextSafe(context, true);
        context.runOnContext(ignored -> {
            try {
                Panache.withTransaction(action).subscribe().with(_ -> done.complete(null), done::completeExceptionally);
            } catch (Throwable throwable) {
                done.completeExceptionally(throwable);
            }
        });

        try {
            done.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the Vert.x context", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not run the action on the Vert.x context", e);
        }
    }
}
