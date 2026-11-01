package app.fyreplace.grpc.endpoints.chapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.ChapterImageUpdate;
import app.fyreplace.protos.ImageChunk;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class UpdateImageTests extends ChapterTestsBase {
    @Test
    @RunOnVertxContext
    public void testStoreImage(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u))
                                        .updateImage(pngUpload(postId, 0))
                                        .flatMap(image ->
                                                chapterAt(postId, 0).map(chapter -> Tuple2.of(image, chapter))))),
                pair -> {
                    final var image = pair.getItem1();
                    assertFalse(image.getUrl().isEmpty());
                    assertEquals(10, image.getWidth());
                    assertEquals(10, image.getHeight());

                    final var chapter = pair.getItem2();
                    assertNotNull(chapter.getImage());
                    assertEquals(10, chapter.getWidth());
                    assertEquals(10, chapter.getHeight());
                    assertTrue(chapter.getText().isEmpty());
                });
    }

    @Test
    @RunOnVertxContext
    public void testEmptyUploadRemovesImage(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u))
                                        .updateImage(pngUpload(postId, 0))
                                        .chain(() ->
                                                chapterService(token(u)).updateImage(locationOnlyUpload(postId, 0)))
                                        .flatMap(image ->
                                                chapterAt(postId, 0).map(chapter -> Tuple2.of(image, chapter))))),
                pair -> {
                    assertEquals("", pair.getItem1().getUrl());
                    assertEquals(0, pair.getItem1().getWidth());
                    assertEquals(0, pair.getItem1().getHeight());
                    assertNull(pair.getItem2().getImage());
                    assertEquals(0, pair.getItem2().getWidth());
                    assertEquals(0, pair.getItem2().getHeight());
                });
    }

    @Test
    @RunOnVertxContext
    public void testBadFormat(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u)).updateImage(textUpload(postId, 0)))),
                Status.Code.INVALID_ARGUMENT,
                "invalid_image");
    }

    @Test
    @RunOnVertxContext
    public void testMissingLocation(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u))
                                        .updateImage(Multi.createFrom()
                                                .item(ChapterImageUpdate.newBuilder()
                                                        .setChunk(ImageChunk.newBuilder()
                                                                .setData(ByteString.copyFromUtf8("png"))
                                                                .build())
                                                        .build())))),
                Status.Code.INVALID_ARGUMENT,
                "missing_location");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidPosition(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u)).updateImage(pngUpload(postId, 5)))),
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
                                .flatMap(u -> chapterService(token(u)).updateImage(pngUpload(post.getId(), 0)))),
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
                                .flatMap(u -> chapterService(token(u)).updateImage(pngUpload(post.getId(), 0)))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testPostNotFound(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> chapterService(token(u)).updateImage(pngUpload(UUID.randomUUID(), 0))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> chapterService().updateImage(pngUpload(UUID.randomUUID(), 0)),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testPayloadTooLarge(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u))
                                        .updateImage(Multi.createFrom()
                                                .items(
                                                        ChapterImageUpdate.newBuilder()
                                                                .setLocation(location(postId, 0))
                                                                .build(),
                                                        ChapterImageUpdate.newBuilder()
                                                                .setChunk(ImageChunk.newBuilder()
                                                                        .setData(ByteString.copyFrom(
                                                                                new byte[512 * 1024 + 1]))
                                                                        .build())
                                                                .build())))),
                Status.Code.INVALID_ARGUMENT,
                "payload_too_large");
    }

    @Test
    @RunOnVertxContext
    public void testReplacementDeletesOldFile(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(u -> draftWithChapters(u, 1)
                                .flatMap(postId -> chapterService(token(u))
                                        .updateImage(pngUpload(postId, 0))
                                        .chain(() -> chapterAt(postId, 0))
                                        .map(first -> Tuple2.of(
                                                postId, first.getImage().getId())))
                                .flatMap(pair -> chapterService(token(u))
                                        .updateImage(pngUpload(pair.getItem1(), 0))
                                        .chain(() -> chapterAt(pair.getItem1(), 0))
                                        .flatMap(second -> session(() -> storedFileRepository.findById(pair.getItem2()))
                                                .map(oldFile -> Tuple2.of(second, oldFile))))),
                pair -> {
                    assertNotNull(pair.getItem1().getImage());
                    assertNull(pair.getItem2());
                });
    }
}
