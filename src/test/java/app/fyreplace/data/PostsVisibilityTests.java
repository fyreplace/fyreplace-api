package app.fyreplace.data;

import app.fyreplace.data.entities.User;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@RunOnVertxContext
public final class PostsVisibilityTests extends DataTestsBase {
    @Test
    void testDeletedPostsAreHiddenFromAuthorReads(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertEquals(
                () -> user1Id().map(postRepository::findByAuthorId).flatMap(PanacheQuery::count), 1L);
        asserter.assertEquals(() -> user1Id().flatMap(authorId -> countByAuthor(authorId, true)), 0L);
        asserter.assertEquals(() -> user1Id().flatMap(authorId -> countByAuthor(authorId, false)), 1L);
    }

    private @Nonnull Uni<UUID> user1Id() {
        return userRepository.findByUsername("user_1").map(User::getId);
    }

    private @Nonnull Uni<Long> countByAuthor(@Nonnull UUID authorId, boolean published) {
        return postRepository
                .findByAuthorId(authorId, published, false)
                .list()
                .map(List::size)
                .map(Integer::longValue);
    }
}
