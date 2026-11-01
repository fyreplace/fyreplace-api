package app.fyreplace.services;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@ApplicationScoped
public final class ExecutorService {
    @Inject
    Vertx vertx;

    public @Nonnull Context safeContext() {
        final var context = VertxContext.getOrCreateDuplicatedContext(vertx.getOrCreateContext());
        VertxContextSafetyToggle.setContextSafe(context, true);
        return context;
    }

    public @Nonnull Executor safeExecutor() {
        final var context = safeContext();
        return command -> context.runOnContext(_ -> command.run());
    }

    public <E> void awaitOnSafeContext(@Nonnull Supplier<Uni<E>> action) {
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
