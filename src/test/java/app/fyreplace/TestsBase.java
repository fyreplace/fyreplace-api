package app.fyreplace;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public abstract class TestsBase {
    @Inject
    protected Vertx vertx;

    protected @Nonnull Context safeContext() {
        final var context = VertxContext.getOrCreateDuplicatedContext(vertx.getOrCreateContext());
        VertxContextSafetyToggle.setContextSafe(context, true);
        return context;
    }

    protected final <T> @Nonnull Uni<T> session(@Nonnull Supplier<Uni<T>> action) {
        final var executor = safeExecutor();
        return Uni.createFrom()
                .voidItem()
                .onItem()
                .transformToUni(_ -> Panache.withSession(action))
                .runSubscriptionOn(executor);
    }

    protected final <T> @Nonnull Uni<T> transaction(@Nonnull Supplier<Uni<T>> action) {
        final var executor = safeExecutor();
        return Uni.createFrom()
                .voidItem()
                .onItem()
                .transformToUni(_ -> Panache.withTransaction(action))
                .runSubscriptionOn(executor);
    }

    protected static <A, B> @Nonnull Uni<Tuple2<A, B>> joinAsTuple(@Nonnull Uni<A> aUni, @Nonnull Uni<B> bUni) {
        return aUni.flatMap(a -> bUni.map(b -> Tuple2.of(a, b)));
    }

    protected static @Nonnull Uni<Void> chainAll(@Nonnull List<? extends Supplier<? extends Uni<?>>> steps) {
        return Multi.createFrom()
                .iterable(steps)
                .concatMap(step -> step.get().toMulti())
                .collect()
                .asList()
                .replaceWithVoid();
    }

    private @Nonnull Executor safeExecutor() {
        final var context = safeContext();
        return command -> context.runOnContext(_ -> command.run());
    }

    protected <E> void awaitOnSafeContext(@Nonnull Supplier<Uni<E>> action) {
        final var done = new CompletableFuture<Void>();

        safeContext().runOnContext(ignored -> {
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
