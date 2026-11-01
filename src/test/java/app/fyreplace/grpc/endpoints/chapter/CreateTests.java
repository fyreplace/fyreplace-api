package app.fyreplace.grpc.endpoints.chapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.fyreplace.data.entities.Chapter;
import app.fyreplace.data.entities.Post;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class CreateTests extends ChapterTestsBase {
    @Test
    @RunOnVertxContext
    public void testCreateAtEnd(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 2)
                                .flatMap(postId -> chapterService(token(u))
                                        .create(location(postId, 2))
                                        .chain(() -> chaptersOf(postId)))),
                chapters -> {
                    assertEquals(3, chapters.size());
                    assertEquals(
                            List.of(0, 1, 2),
                            chapters.stream().map(Chapter::getPosition).toList());
                });
    }

    @Test
    @RunOnVertxContext
    public void testCreateInMiddleShiftsPositions(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 2)
                                .flatMap(postId -> chapterService(token(u))
                                        .create(location(postId, 1))
                                        .call(() ->
                                                chapterService(token(u)).updateText(textUpdate(postId, 1, "inserted")))
                                        .chain(() -> chaptersOf(postId)))),
                chapters -> assertEquals(
                        List.of("chapter_0", "inserted", "chapter_1"),
                        chapters.stream().map(Chapter::getText).toList()));
    }

    @Test
    @RunOnVertxContext
    public void testInvalidPosition(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u)).create(location(postId, 2)))),
                Status.Code.INVALID_ARGUMENT,
                "invalid_position");
    }

    @Test
    @RunOnVertxContext
    public void testTooManyChapters(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> newDraft(u).flatMap(postId -> {
                            final var service = chapterService(token(u));
                            Uni<Empty> chain = Uni.createFrom().item(Empty.getDefaultInstance());

                            for (var i = 0; i < Post.MAX_CHAPTERS; i++) {
                                final var position = i;
                                chain = chain.call(() -> service.create(location(postId, position)));
                            }

                            return chain.call(() -> service.create(location(postId, Post.MAX_CHAPTERS)));
                        })),
                Status.Code.PERMISSION_DENIED,
                "too_many_chapters");
    }

    @Test
    @RunOnVertxContext
    public void testPublishedPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postOf(ACTIVE_USER, true, 0)
                        .flatMap(post -> user(ACTIVE_USER)
                                .flatMap(u -> chapterService(token(u)).create(location(post.getId(), 0)))),
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
                                .flatMap(u -> chapterService(token(u)).create(location(post.getId(), 0)))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testPostNotFound(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER).flatMap(u -> chapterService(token(u)).create(location(UUID.randomUUID(), 0))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> chapterService().create(location(UUID.randomUUID(), 0)),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testDeletedPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> newDraft(u)
                                .flatMap(postId -> postService(token(u))
                                        .delete(toProtoService.id(postId))
                                        .replaceWith(postId))
                                .flatMap(postId -> chapterService(token(u)).create(location(postId, 0)))),
                Status.Code.NOT_FOUND,
                null);
    }
}
