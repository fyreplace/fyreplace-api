package app.fyreplace.data;

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
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@RunOnVertxContext
public final class SubscriptionsTriggerTests extends DataTestsBase {
    @Test
    void testLatestCommentIsSeenEvenIfDeleted(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() -> loadFixtures()
                .flatMap(fixtures -> subscribeAndAssert(fixtures.author(), fixtures.post(), created -> {
                    Assertions.assertNotNull(
                            created.getLastCommentSeen(), "the trigger did not fill last_comment_seen_id");
                    Assertions.assertEquals(
                            fixtures.latestComment().getId(),
                            created.getLastCommentSeen().getId(),
                            "the latest comment must be marked as seen, even if deleted");
                })));
    }

    @Test
    void testSubscribeToPostWithoutComments(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() -> userRepository
                .findByUsername("trigger_author")
                .flatMap(author -> postRepository
                        .findByAuthorId(author.getId(), false, true)
                        .firstResult()
                        .flatMap(post -> subscribeAndAssert(
                                author,
                                post,
                                created -> Assertions.assertNull(
                                        created.getLastCommentSeen(),
                                        "subscribing to a comment-less post must leave last_comment_seen_id unset")))));
    }

    private @Nonnull Uni<Void> subscribeAndAssert(
            @Nonnull User author, @Nonnull Post post, @Nonnull Consumer<Subscription> assertions) {
        final var subscription = new Subscription();
        subscription.setUser(author);
        subscription.setPost(post);
        subscription.setDateLastSeen(OffsetDateTime.now());
        return subscriptionRepository
                .persist(subscription)
                .flatMap(SubscriptionsTriggerTests::refresh)
                .invoke(assertions)
                .flatMap(created -> subscriptionRepository.delete(created))
                .replaceWithVoid();
    }

    private @Nonnull Uni<Fixtures> loadFixtures() {
        return userRepository
                .findByUsername("trigger_author")
                .flatMap(author -> postRepository
                        .findByAuthorId(author.getId(), true, true)
                        .firstResult()
                        .flatMap(post -> commentRepository
                                .findByPostId(post.getId())
                                .list()
                                .map(latestComment -> new Fixtures(author, post, latestComment.getLast()))));
    }

    private record Fixtures(
            @Nonnull User author,
            @Nonnull Post post,
            @Nonnull Comment latestComment) {}
}
