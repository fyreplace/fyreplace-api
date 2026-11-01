package app.fyreplace.grpc.endpoints.chapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class UpdateTextTests extends ChapterTestsBase {
    @Test
    @RunOnVertxContext
    public void testUpdateText(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u))
                                        .updateText(textUpdate(postId, 0, "hello"))
                                        .chain(() -> chapterAt(postId, 0)))),
                chapter -> assertEquals("hello", chapter.getText()));
    }

    @Test
    @RunOnVertxContext
    public void testUpdateTextClearsImage(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u))
                                        .updateImage(pngUpload(postId, 0))
                                        .call(() -> chapterService(token(u)).updateText(textUpdate(postId, 0, "hello")))
                                        .chain(() -> chapterAt(postId, 0)))),
                chapter -> {
                    assertEquals("hello", chapter.getText());
                    assertNull(chapter.getImage());
                    assertEquals(0, chapter.getWidth());
                    assertEquals(0, chapter.getHeight());
                });
    }

    @Test
    @RunOnVertxContext
    public void testEmptyTextAllowed(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u))
                                        .updateText(textUpdate(postId, 0, ""))
                                        .chain(() -> chapterAt(postId, 0)))),
                chapter -> assertEquals("", chapter.getText()));
    }

    @Test
    @RunOnVertxContext
    public void testTextTooLong(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId ->
                                        chapterService(token(u)).updateText(textUpdate(postId, 0, "x".repeat(501))))),
                Status.Code.INVALID_ARGUMENT,
                "invalid_text");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidPosition(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(
                                        postId -> chapterService(token(u)).updateText(textUpdate(postId, 5, "hello")))),
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
                                .flatMap(u ->
                                        chapterService(token(u)).updateText(textUpdate(post.getId(), 0, "hello")))),
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
                                .flatMap(u ->
                                        chapterService(token(u)).updateText(textUpdate(post.getId(), 0, "hello")))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testPostNotFound(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> chapterService(token(u)).updateText(textUpdate(UUID.randomUUID(), 0, "hello"))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> chapterService().updateText(textUpdate(UUID.randomUUID(), 0, "hello")),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
