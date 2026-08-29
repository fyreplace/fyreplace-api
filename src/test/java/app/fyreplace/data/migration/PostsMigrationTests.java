package app.fyreplace.data.migration;

import app.fyreplace.data.entities.Post;
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
@RunOnVertxContext
public final class PostsMigrationTests extends MigrationTestsBase {
    @Test
    void testEntityCount(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertEquals(postRepository::count, 8L);
        asserter.assertEquals(chapterRepository::count, 7L);
    }

    @Test
    void testUser0Post0(@Nonnull TransactionalUniAsserter asserter) {
        final Supplier<Uni<Post>> postSupplier =
                () -> getPostsFromUser("user_0").map(List::getFirst);

        asserter.assertThat(postSupplier, p -> {
            Assertions.assertEquals(OffsetDateTime.parse("2025-01-12T00:00:00Z"), p.getDateCreated());
            Assertions.assertTrue(p.isPublished());
            Assertions.assertEquals(4, p.getLife());
            Assertions.assertFalse(p.isAnonymous());
            Assertions.assertFalse(p.isDeleted());
        });

        asserter.assertEquals(() -> postSupplier.get().map(Post::getChapters).map(List::size), 2);

        asserter.assertThat(() -> postSupplier.get().map(Post::getChapters).map(List::getFirst), c -> {
            Assertions.assertEquals(0, c.getPosition());
            Assertions.assertEquals("1st chapter of the 1st post from user_0", c.getText());
            Assertions.assertNull(c.getImage());
            Assertions.assertEquals(0, c.getWidth());
            Assertions.assertEquals(0, c.getHeight());
        });

        asserter.assertThat(() -> postSupplier.get().map(Post::getChapters).map(List::getLast), c -> {
            Assertions.assertEquals(1, c.getPosition());
            Assertions.assertEquals("2nd chapter of the 1st post from user_0", c.getText());
            Assertions.assertNull(c.getImage());
            Assertions.assertEquals(0, c.getWidth());
            Assertions.assertEquals(0, c.getHeight());
        });
    }

    @Test
    void testUser0Post1(@Nonnull TransactionalUniAsserter asserter) {
        final Supplier<Uni<Post>> postSupplier =
                () -> getPostsFromUser("user_0").map(List::getLast);

        asserter.assertThat(postSupplier, p -> {
            Assertions.assertEquals(OffsetDateTime.parse("2025-06-25T00:00:00Z"), p.getDateCreated());
            Assertions.assertTrue(p.isPublished());
            Assertions.assertEquals(3, p.getLife());
            Assertions.assertTrue(p.isAnonymous());
            Assertions.assertFalse(p.isDeleted());
        });

        asserter.assertEquals(() -> postSupplier.get().map(Post::getChapters).map(List::size), 0);
    }

    @Test
    void testUser1Post0(@Nonnull TransactionalUniAsserter asserter) {
        final Supplier<Uni<Post>> postSupplier =
                () -> getPostsFromUser("user_1").map(List::getFirst);

        asserter.assertThat(postSupplier, p -> {
            Assertions.assertEquals(OffsetDateTime.parse("2025-03-03T00:00:00Z"), p.getDateCreated());
            Assertions.assertTrue(p.isPublished());
            Assertions.assertEquals(5, p.getLife());
            Assertions.assertFalse(p.isAnonymous());
            Assertions.assertTrue(p.isDeleted());
        });

        asserter.assertEquals(() -> postSupplier.get().map(Post::getChapters).map(List::size), 1);

        asserter.assertThat(() -> postSupplier.get().map(Post::getChapters).map(List::getFirst), c -> {
            Assertions.assertEquals(0, c.getPosition());
            Assertions.assertEquals("", c.getText());
            Assertions.assertNotNull(c.getImage());
            Assertions.assertEquals("chapters/some_pic.jpeg", c.getImage().getPath());
            Assertions.assertEquals(1280, c.getWidth());
            Assertions.assertEquals(720, c.getHeight());
        });
    }

    @Test
    void testUser1Post1(@Nonnull TransactionalUniAsserter asserter) {
        final Supplier<Uni<Post>> postSupplier =
                () -> getPostsFromUser("user_1").map(List::getLast);

        asserter.assertThat(postSupplier, p -> {
            Assertions.assertEquals(OffsetDateTime.parse("2025-04-12T00:00:00Z"), p.getDateCreated());
            Assertions.assertFalse(p.isPublished());
            Assertions.assertEquals(0, p.getLife());
            Assertions.assertFalse(p.isAnonymous());
            Assertions.assertFalse(p.isDeleted());
        });

        asserter.assertEquals(() -> postSupplier.get().map(Post::getChapters).map(List::size), 0);
    }
}
