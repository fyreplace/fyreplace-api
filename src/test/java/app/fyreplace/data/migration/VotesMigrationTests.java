package app.fyreplace.data.migration;

import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.User;
import app.fyreplace.data.entities.Vote;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

@QuarkusTest
public final class VotesMigrationTests extends MigrationTestsBase {
    @Test
    @RunOnVertxContext
    void testEntityCount(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertEquals(voteRepository::count, 2L);
    }

    @Test
    @RunOnVertxContext
    void testUser0Vote(@Nonnull TransactionalUniAsserter asserter) {
        final Supplier<Uni<User>> userSupplier = () -> userRepository.findByUsername("user_0");
        final Supplier<Uni<Post>> postSupplier =
                () -> getPostsFromUser("user_1").map(List::getFirst);
        final Supplier<Uni<Vote>> voteSupplier = () -> joinAsTuple(userSupplier.get(), postSupplier.get())
                .flatMap(t -> voteRepository.findByUserIdAndPostId(
                        t.getItem1().getId(), t.getItem2().getId()));

        asserter.assertTrue(() -> voteSupplier.get().map(Vote::isSpread));
    }

    @Test
    @RunOnVertxContext
    void testUser1Vote(@Nonnull TransactionalUniAsserter asserter) {
        final Supplier<Uni<User>> userSupplier = () -> userRepository.findByUsername("user_1");
        final Supplier<Uni<Post>> postSupplier =
                () -> getPostsFromUser("user_0").map(List::getFirst);
        final Supplier<Uni<Vote>> voteSupplier = () -> joinAsTuple(userSupplier.get(), postSupplier.get())
                .flatMap(t -> voteRepository.findByUserIdAndPostId(
                        t.getItem1().getId(), t.getItem2().getId()));

        asserter.assertFalse(() -> voteSupplier.get().map(Vote::isSpread));
    }
}
