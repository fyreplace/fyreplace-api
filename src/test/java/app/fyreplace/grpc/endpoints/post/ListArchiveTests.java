package app.fyreplace.grpc.endpoints.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Publication;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class ListArchiveTests extends PostTestsBase {
    @Test
    @RunOnVertxContext
    public void testArchive(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .listArchive(paginateRequest(10, 0))
                                .collect()
                                .asList()
                                .map(bundles -> Tuple2.of(pair.getItem2(), bundles))),
                pair -> {
                    final var bundles = pair.getItem2();
                    assertEquals(1, bundles.size());
                    final var bundle = bundles.getFirst();
                    assertEquals(1, bundle.getPostsCount());
                    final var post = bundle.getPosts(0);
                    assertEquals(toProtoService.byteString(pair.getItem1().getId()), post.getId());
                    assertTrue(post.getIsPreview());
                    assertEquals(2, post.getChapterCount());
                    assertEquals(1, post.getChaptersCount());
                    assertTrue(post.getIsSubscribed());
                    assertEquals(1, post.getCommentsRead());
                });
    }

    @Test
    @RunOnVertxContext
    public void testEmpty(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .listArchive(paginateRequest(10, 0))
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
                                .listArchive(paginateRequest(10, 5))
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
    public void testDeletedPostExcluded(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .create(Empty.getDefaultInstance())
                                .map(id -> Tuple2.of(active, uuid(id))))
                        .flatMap(pair -> addChapter(pair.getItem2(), "hello").replaceWith(pair))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .publish(Publication.newBuilder()
                                        .setId(toProtoService.byteString(pair.getItem2()))
                                        .build())
                                .replaceWith(pair))
                        .flatMap(pair -> user(OTHER_USER)
                                .flatMap(other -> postService(token(other))
                                        .updateSubscription(subscription(pair.getItem2(), true))
                                        .replaceWith(Tuple2.of(pair, other))))
                        .flatMap(triple -> postService(token(triple.getItem1().getItem1()))
                                .delete(toProtoService.id(triple.getItem1().getItem2()))
                                .replaceWith(triple))
                        .flatMap(triple -> postService(token(triple.getItem2()))
                                .listArchive(paginateRequest(10, 0))
                                .collect()
                                .asList()
                                .map(bundles -> Tuple2.of(triple.getItem1().getItem2(), bundles))),
                pair -> assertFalse(pair.getItem2().getFirst().getPostsList().stream()
                        .anyMatch(post -> post.getId().equals(toProtoService.byteString(pair.getItem1())))));
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postService()
                        .listArchive(paginateRequest(10, 0))
                        .collect()
                        .asList(),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    private @Nonnull app.fyreplace.protos.Subscription subscription(@Nonnull UUID postId, boolean subscribed) {
        return app.fyreplace.protos.Subscription.newBuilder()
                .setId(toProtoService.byteString(postId))
                .setSubscribed(subscribed)
                .build();
    }
}
