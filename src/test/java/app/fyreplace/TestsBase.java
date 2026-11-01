package app.fyreplace;

import app.fyreplace.services.ExecutorService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import java.util.List;
import java.util.function.Supplier;

public abstract class TestsBase {
    @Inject
    protected ExecutorService executorService;

    protected final <T> @Nonnull Uni<T> session(@Nonnull Supplier<Uni<T>> action) {
        return Uni.createFrom()
                .voidItem()
                .onItem()
                .transformToUni(_ -> Panache.withSession(action))
                .runSubscriptionOn(executorService.safeExecutor());
    }

    protected final <T> @Nonnull Uni<T> transaction(@Nonnull Supplier<Uni<T>> action) {
        return Uni.createFrom()
                .voidItem()
                .onItem()
                .transformToUni(_ -> Panache.withTransaction(action))
                .runSubscriptionOn(executorService.safeExecutor());
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
}
