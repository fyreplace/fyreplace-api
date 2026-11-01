package app.fyreplace.grpc.endpoints.chapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.fyreplace.data.entities.Chapter;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class MoveTests extends ChapterTestsBase {
    @Test
    @RunOnVertxContext
    public void testMoveForward(@Nonnull UniAsserter asserter) {
        assertMove(asserter, 0, 2, List.of("chapter_1", "chapter_2", "chapter_0", "chapter_3"));
    }

    @Test
    @RunOnVertxContext
    public void testMoveBackward(@Nonnull UniAsserter asserter) {
        assertMove(asserter, 3, 1, List.of("chapter_0", "chapter_3", "chapter_1", "chapter_2"));
    }

    @Test
    @RunOnVertxContext
    public void testMoveSamePosition(@Nonnull UniAsserter asserter) {
        assertMove(asserter, 2, 2, List.of("chapter_0", "chapter_1", "chapter_2", "chapter_3"));
    }

    @Test
    @RunOnVertxContext
    public void testInvalidFromPosition(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 4)
                                .flatMap(postId -> chapterService(token(u)).move(relocation(postId, 7, 0)))),
                Status.Code.INVALID_ARGUMENT,
                "invalid_position");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidToPosition(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 4)
                                .flatMap(postId -> chapterService(token(u)).move(relocation(postId, 0, 4)))),
                Status.Code.INVALID_ARGUMENT,
                "invalid_position");
    }

    @Test
    @RunOnVertxContext
    public void testPublishedPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postOf(ACTIVE_USER, true, 0)
                        .flatMap(post -> user(ACTIVE_USER)
                                .flatMap(u -> chapterService(token(u)).move(relocation(post.getId(), 0, 1)))),
                Status.Code.PERMISSION_DENIED,
                "post_published");
    }

    @Test
    @RunOnVertxContext
    public void testForeignDraftNotReadable(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postOf(OTHER_USER, false, 0)
                        .flatMap(post -> user(ACTIVE_USER)
                                .flatMap(u -> chapterService(token(u)).move(relocation(post.getId(), 0, 0)))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testPostNotFound(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> chapterService(token(u)).move(relocation(UUID.randomUUID(), 0, 1))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> chapterService().move(relocation(UUID.randomUUID(), 0, 1)),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    private void assertMove(
            @Nonnull UniAsserter asserter, int fromPosition, int toPosition, @Nonnull List<String> expected) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 4)
                                .flatMap(postId -> chapterService(token(u))
                                        .move(relocation(postId, fromPosition, toPosition))
                                        .chain(() -> chaptersOf(postId)))),
                chapters -> assertEquals(
                        expected, chapters.stream().map(Chapter::getText).toList()));
    }
}
