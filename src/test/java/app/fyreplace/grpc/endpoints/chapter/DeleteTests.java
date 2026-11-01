package app.fyreplace.grpc.endpoints.chapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class DeleteTests extends ChapterTestsBase {
    @Test
    @RunOnVertxContext
    public void testDeleteCompactsPositions(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 2)
                                .flatMap(postId -> chapterService(token(u))
                                        .delete(location(postId, 0))
                                        .chain(() -> chaptersOf(postId)))),
                chapters -> {
                    assertEquals(1, chapters.size());
                    assertEquals(0, chapters.getFirst().getPosition());
                    assertEquals("chapter_1", chapters.getFirst().getText());
                });
    }

    @Test
    @RunOnVertxContext
    public void testDeleteRemovesStoredFile(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u))
                                        .updateImage(pngUpload(postId, 0))
                                        .chain(() -> chapterAt(postId, 0))
                                        .flatMap(chapter -> chapterService(token(u))
                                                .delete(location(postId, 0))
                                                .chain(() -> session(() -> storedFileRepository.findById(
                                                        chapter.getImage().getId())))
                                                .map(Objects::isNull)))),
                Assertions::assertTrue);
    }

    @Test
    @RunOnVertxContext
    public void testInvalidPosition(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u)).delete(location(postId, 5)))),
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
                                .flatMap(u -> chapterService(token(u)).delete(location(post.getId(), 0)))),
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
                                .flatMap(u -> chapterService(token(u)).delete(location(post.getId(), 0)))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testPostNotFound(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER).flatMap(u -> chapterService(token(u)).delete(location(UUID.randomUUID(), 0))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> chapterService().delete(location(UUID.randomUUID(), 0)),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
