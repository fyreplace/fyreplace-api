package app.fyreplace.data;

import app.fyreplace.data.entities.Chapter;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.User;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public final class ChaptersMoveTests extends DataTestsBase {
    private static final String AUTHOR_USERNAME = "chapter_author";
    private static final List<String> ORIGINAL_LAYOUT =
            List.of("chapter_0/10", "chapter_1/20", "chapter_2/30", "chapter_3/40");

    @Test
    @RunOnVertxContext
    void testForwardMove(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() -> resolvePostId()
                .flatMap(postId -> assertLayout(postId, ORIGINAL_LAYOUT)
                        .call(() -> chapterRepository.moveByLocation(new Chapter.Location(postId, 1), 3))
                        .call(() -> assertLayout(
                                postId, List.of("chapter_0/10", "chapter_2/30", "chapter_3/40", "chapter_1/20")))
                        .call(() -> chapterRepository.moveByLocation(new Chapter.Location(postId, 3), 1))
                        .call(() -> assertLayout(postId, ORIGINAL_LAYOUT))));
    }

    @Test
    @RunOnVertxContext
    void testBackwardMove(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() -> resolvePostId()
                .flatMap(postId -> assertLayout(postId, ORIGINAL_LAYOUT)
                        .call(() -> chapterRepository.moveByLocation(new Chapter.Location(postId, 3), 1))
                        .call(() -> assertLayout(
                                postId, List.of("chapter_0/10", "chapter_3/40", "chapter_1/20", "chapter_2/30")))
                        .call(() -> chapterRepository.moveByLocation(new Chapter.Location(postId, 1), 3))
                        .call(() -> assertLayout(postId, ORIGINAL_LAYOUT))));
    }

    @Test
    @RunOnVertxContext
    void testMoveToOwnPositionKeepsLayout(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() -> resolvePostId()
                .flatMap(postId -> chapterRepository
                        .moveByLocation(new Chapter.Location(postId, 2), 2)
                        .call(() -> assertLayout(postId, ORIGINAL_LAYOUT))));
    }

    @Test
    @RunOnVertxContext
    void testMoveFromMissingPositionIsNoOp(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() -> resolvePostId()
                .flatMap(postId -> chapterRepository
                        .moveByLocation(new Chapter.Location(postId, 7), 0)
                        .call(() -> assertLayout(postId, ORIGINAL_LAYOUT))));
    }

    private @Nonnull Uni<UUID> resolvePostId() {
        return userRepository
                .findByUsername(AUTHOR_USERNAME)
                .map(User::getId)
                .map(postRepository::findByAuthorId)
                .flatMap(PanacheQuery::list)
                .map(List::getFirst)
                .map(Post::getId);
    }

    private @Nonnull Uni<Void> assertLayout(@Nonnull UUID postId, @Nonnull List<String> expected) {
        return chapterRepository
                .findByPostId(postId)
                .list()
                .map(List::stream)
                .map(chapters -> chapters.map(chapter -> chapter.getText() + "/" + chapter.getWidth()))
                .map(Stream::toList)
                .invoke(labels -> Assertions.assertEquals(expected, labels))
                .replaceWithVoid();
    }
}
