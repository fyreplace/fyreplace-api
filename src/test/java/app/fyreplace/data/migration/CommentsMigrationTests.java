package app.fyreplace.data.migration;

import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.Post;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public final class CommentsMigrationTests extends MigrationTestsBase {
    @Test
    @RunOnVertxContext
    void testEntityCount(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertEquals(commentRepository::count, 6L);
    }

    @Test
    @RunOnVertxContext
    void testCommentFromUser1(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertThat(() -> getCommentsFromPost().map(List::getFirst), comment -> {
            Assertions.assertEquals("user_1", comment.getAuthor().getUsername());
            Assertions.assertEquals(OffsetDateTime.parse("2025-10-01T00:00:00Z"), comment.getDateCreated());
            Assertions.assertEquals("Howdy from user_1!", comment.getText());
            Assertions.assertFalse(comment.isAnonymous());
            Assertions.assertFalse(comment.isDeleted());
        });
    }

    @Test
    @RunOnVertxContext
    void testCommentFromUser2(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertThat(() -> getCommentsFromPost().map(c -> c.get(1)), comment -> {
            Assertions.assertEquals("user_2", comment.getAuthor().getUsername());
            Assertions.assertEquals(OffsetDateTime.parse("2025-10-02T00:00:00Z"), comment.getDateCreated());
            Assertions.assertEquals("Howdy also from user_2!", comment.getText());
            Assertions.assertFalse(comment.isAnonymous());
            Assertions.assertFalse(comment.isDeleted());
        });
    }

    @Test
    @RunOnVertxContext
    void testCommentDeleted(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertThat(() -> getCommentsFromPost().map(c -> c.get(2)), comment -> {
            Assertions.assertEquals("user_1", comment.getAuthor().getUsername());
            Assertions.assertEquals(OffsetDateTime.parse("2025-10-03T00:00:00Z"), comment.getDateCreated());
            Assertions.assertEquals("", comment.getText());
            Assertions.assertFalse(comment.isAnonymous());
            Assertions.assertTrue(comment.isDeleted());
        });
    }

    private @Nonnull Uni<List<Comment>> getCommentsFromPost() {
        return getPostsFromUser("user_0")
                .map(List::getFirst)
                .map(Post::getId)
                .map(commentRepository::findByPostId)
                .flatMap(PanacheQuery::list);
    }
}
