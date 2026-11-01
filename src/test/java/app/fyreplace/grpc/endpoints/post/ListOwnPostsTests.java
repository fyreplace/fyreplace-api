package app.fyreplace.grpc.endpoints.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Cursor;
import app.fyreplace.protos.Header;
import app.fyreplace.protos.KeyValuePair;
import app.fyreplace.protos.Page;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class ListOwnPostsTests extends PostTestsBase {
    @Test
    @RunOnVertxContext
    public void testOwnPosts(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(ACTIVE_USER), postOf(ACTIVE_USER, true, 0))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .listOwnPosts(paginateRequest(10, 0))
                                .collect()
                                .asList()
                                .map(bundles -> Tuple2.of(pair.getItem2(), bundles))),
                pair -> {
                    final var bundles = pair.getItem2();
                    assertEquals(1, bundles.size());
                    final var posts = bundles.getFirst().getPostsList();
                    assertEquals(2, posts.size());
                    // forward=false: newest first, the anonymous post seeded last
                    assertEquals(
                            toProtoService.byteString(pair.getItem1().getId()),
                            posts.get(0).getId());
                    assertTrue(posts.get(0).getIsAnonymous());
                    assertTrue(posts.get(0).getIsPreview());
                    assertEquals(0, posts.get(0).getChapterCount());
                    assertEquals(ACTIVE_USER, posts.get(0).getAuthor().getUsername());
                    assertEquals(2, posts.get(1).getChapterCount());
                    assertEquals(1, posts.get(1).getChaptersCount());
                });
    }

    @Test
    @RunOnVertxContext
    public void testPagination(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .listOwnPosts(paginateRequest(1, 0, 1))
                                .collect()
                                .asList()),
                bundles -> {
                    assertEquals(2, bundles.size());
                    assertEquals(1, bundles.get(0).getPostsCount());
                    assertTrue(bundles.get(0).hasNext());
                    assertEquals(1, bundles.get(1).getPostsCount());
                    assertTrue(bundles.get(1).hasPrevious());
                });
    }

    @Test
    @RunOnVertxContext
    public void testReverse(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> Uni.combine()
                                .all()
                                .unis(
                                        postService(token(active))
                                                .listOwnPosts(paginateRequest(false, 10, 0))
                                                .collect()
                                                .asList(),
                                        postService(token(active))
                                                .listOwnPosts(paginateRequest(true, 10, 0))
                                                .collect()
                                                .asList())
                                .asTuple()),
                tuple -> {
                    final var backward = tuple.getItem1().getFirst().getPostsList();
                    final var forward = tuple.getItem2().getFirst().getPostsList();
                    assertEquals(2, backward.size());
                    assertEquals(2, forward.size());
                    assertEquals(backward.get(0).getId(), forward.get(1).getId());
                    assertEquals(backward.get(1).getId(), forward.get(0).getId());
                });
    }

    @Test
    @RunOnVertxContext
    public void testOutOfBounds(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .listOwnPosts(paginateRequest(10, 5))
                                .collect()
                                .asList()),
                bundles -> {
                    assertEquals(1, bundles.size());
                    final var bundle = bundles.getFirst();
                    assertEquals(0, bundle.getPostsCount());
                    assertFalse(bundle.hasNext());
                });
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postService()
                        .listOwnPosts(paginateRequest(10, 0))
                        .collect()
                        .asList(),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testInvalidSize(@Nonnull UniAsserter asserter) {
        for (final var size : new int[] {0, 51}) {
            assertFailure(
                    asserter,
                    () -> user(ACTIVE_USER)
                            .flatMap(active -> postService(token(active))
                                    .listOwnPosts(Multi.createFrom()
                                            .items(
                                                    Page.newBuilder()
                                                            .setHeader(Header.newBuilder()
                                                                    .setForward(false)
                                                                    .setSize(size))
                                                            .build(),
                                                    Page.newBuilder()
                                                            .setCursor(Cursor.newBuilder()
                                                                    .addData(KeyValuePair.newBuilder()
                                                                            .setKey("index")
                                                                            .setValue("0"))
                                                                    .addData(KeyValuePair.newBuilder()
                                                                            .setKey("size")
                                                                            .setValue(String.valueOf(size))))
                                                            .build()))
                                    .collect()
                                    .asList()),
                    Status.Code.INVALID_ARGUMENT,
                    "invalid_size");
        }
    }

    @Test
    @RunOnVertxContext
    public void testMissingHeader(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .listOwnPosts(Multi.createFrom()
                                        .items(Page.newBuilder()
                                                .setCursor(toProtoService.cursor(
                                                        new io.quarkus.panache.common.Page(0, 10)))
                                                .build()))
                                .collect()
                                .asList()),
                Status.Code.INVALID_ARGUMENT,
                "missing_header");
    }

    @Test
    @RunOnVertxContext
    public void testInvalidCursor(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .listOwnPosts(Multi.createFrom()
                                        .items(
                                                Page.newBuilder()
                                                        .setHeader(Header.newBuilder()
                                                                .setForward(false)
                                                                .setSize(10))
                                                        .build(),
                                                Page.newBuilder()
                                                        .setCursor(Cursor.newBuilder()
                                                                .addData(KeyValuePair.newBuilder()
                                                                        .setKey("foo")
                                                                        .setValue("0"))
                                                                .addData(KeyValuePair.newBuilder()
                                                                        .setKey("bar")
                                                                        .setValue("10")))
                                                        .build()))
                                .collect()
                                .asList()),
                Status.Code.INVALID_ARGUMENT,
                "invalid_cursor");
    }

    @Test
    @RunOnVertxContext
    public void testRandomAccessUnauthorized(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .listOwnPosts(Multi.createFrom()
                                        .items(
                                                Page.newBuilder()
                                                        .setHeader(Header.newBuilder()
                                                                .setForward(false)
                                                                .setSize(10))
                                                        .build(),
                                                Page.newBuilder().setOffset(0).build()))
                                .collect()
                                .asList()),
                Status.Code.INVALID_ARGUMENT,
                "random_access_unauthorized");
    }
}
