package app.fyreplace.data.migration;

import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.Subscription;
import app.fyreplace.data.entities.User;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public final class SubscriptionsMigrationTests extends MigrationTestsBase {
    @Test
    @RunOnVertxContext
    void testEntityCount(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertEquals(subscriptionRepository::count, 2L);
    }

    @Test
    @RunOnVertxContext
    void testSubscription(@Nonnull TransactionalUniAsserter asserter) {
        final Supplier<Uni<Post>> postSupplier =
                () -> getPostsFromUser("user_0").map(List::getFirst);
        final Supplier<Uni<Comment>> commentSupplier = () -> postSupplier
                .get()
                .map(Post::getId)
                .flatMap(id -> commentRepository.findByPostId(id).<Comment>firstResult());
        final Supplier<Uni<User>> userSupplier = () -> userRepository.findByUsername("user_1");
        final Supplier<Uni<Subscription>> subscriptionSupplier = () -> userSupplier
                .get()
                .map(User::getId)
                .flatMap(id -> subscriptionRepository.findByUserId(id, true).<Subscription>firstResult());

        asserter.assertThat(
                () -> joinAsTuple(subscriptionSupplier.get(), postSupplier.get()),
                tuple -> Assertions.assertEquals(
                        tuple.getItem2().getId(), tuple.getItem1().getPost().getId()));

        asserter.assertThat(
                () -> joinAsTuple(subscriptionSupplier.get(), commentSupplier.get()),
                tuple -> Assertions.assertEquals(
                        tuple.getItem2().getId(),
                        tuple.getItem1().getLastCommentSeen().getId()));

        asserter.assertEquals(
                () -> subscriptionSupplier.get().map(Subscription::getDateLastSeen),
                OffsetDateTime.parse("2025-10-01T12:00:00Z"));
    }
}
