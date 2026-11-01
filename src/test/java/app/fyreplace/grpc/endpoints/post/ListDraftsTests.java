package app.fyreplace.grpc.endpoints.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class ListDraftsTests extends PostTestsBase {
    @Test
    @RunOnVertxContext
    public void testDrafts(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(OTHER_USER, false, 0))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .listDrafts(paginateRequest(10, 0))
                                .collect()
                                .asList()
                                .map(bundles -> Tuple2.of(pair.getItem2(), bundles))),
                pair -> {
                    final var bundles = pair.getItem2();
                    assertEquals(1, bundles.size());
                    final var posts = bundles.getFirst().getPostsList();
                    assertEquals(1, posts.size());
                    final var post = posts.getFirst();
                    assertEquals(toProtoService.byteString(pair.getItem1().getId()), post.getId());
                    assertTrue(post.getIsPreview());
                    assertEquals(0, post.getChapterCount());
                    assertEquals(OTHER_USER, post.getAuthor().getUsername());
                });
    }

    @Test
    @RunOnVertxContext
    public void testEmpty(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .listDrafts(paginateRequest(10, 0))
                                .collect()
                                .asList()),
                bundles -> {
                    assertEquals(1, bundles.size());
                    assertEquals(0, bundles.getFirst().getPostsCount());
                });
    }

    @Test
    @RunOnVertxContext
    public void testOutOfBounds(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(other -> postService(token(other))
                                .listDrafts(paginateRequest(10, 5))
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
    public void testDeletedDraftExcluded(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .create(Empty.getDefaultInstance())
                                .flatMap(id ->
                                        postService(token(active)).delete(id).replaceWith(active)))
                        .flatMap(active -> postService(token(active))
                                .listDrafts(paginateRequest(10, 0))
                                .collect()
                                .asList()),
                bundles -> assertEquals(0, bundles.getFirst().getPostsCount()));
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postService().listDrafts(paginateRequest(10, 0)).collect().asList(),
                Status.Code.UNAUTHENTICATED,
                null);
    }
}
