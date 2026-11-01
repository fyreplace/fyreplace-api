package app.fyreplace.grpc.endpoints.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.User;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Publication;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class PublishTests extends PostTestsBase {
    @Test
    @RunOnVertxContext
    public void testPublish(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createDraft(ACTIVE_USER, "hello")
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .publish(publication(pair.getItem2(), true))
                                .replaceWith(pair))
                        .flatMap(pair -> session(() -> postRepository.findById(pair.getItem2()))
                                .flatMap(post -> session(() -> subscriptionRepository.findByUserIdAndPostIdOrNull(
                                                pair.getItem1().getId(), pair.getItem2()))
                                        .map(subscription -> Tuple2.of(post, subscription)))),
                pair -> {
                    assertTrue(pair.getItem1().isPublished());
                    assertTrue(pair.getItem1().isAnonymous());
                    assertEquals(Post.INITIAL_LIFE, pair.getItem1().getLife());
                    assertNotNull(pair.getItem2());
                });
    }

    @Test
    @RunOnVertxContext
    public void testPublishEmptyPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> createDraft(ACTIVE_USER, null)
                        .flatMap(pair ->
                                postService(token(pair.getItem1())).publish(publication(pair.getItem2(), false))),
                Status.Code.INVALID_ARGUMENT,
                "post_empty");
    }

    @Test
    @RunOnVertxContext
    public void testPublishEmptyChapter(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> createDraft(ACTIVE_USER, "")
                        .flatMap(pair ->
                                postService(token(pair.getItem1())).publish(publication(pair.getItem2(), false))),
                Status.Code.INVALID_ARGUMENT,
                "chapter_empty");
    }

    @Test
    @RunOnVertxContext
    public void testPublishAlreadyPublished(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .publish(publication(pair.getItem2().getId(), false))),
                Status.Code.PERMISSION_DENIED,
                "post_published");
    }

    @Test
    @RunOnVertxContext
    public void testPublishOthersPublishedPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .publish(publication(pair.getItem2().getId(), false))),
                Status.Code.PERMISSION_DENIED,
                "post_published");
    }

    @Test
    @RunOnVertxContext
    public void testPublishOthersDraft(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), postOf(OTHER_USER, false, 0))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .publish(publication(pair.getItem2().getId(), false))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active)).publish(publication(UUID.randomUUID(), false))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postService().publish(publication(UUID.randomUUID(), false)),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testPublishDeletedPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> createDraft(ACTIVE_USER, "hello")
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .publish(publication(pair.getItem2(), false))
                                .replaceWith(pair))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .delete(toProtoService.id(pair.getItem2()))
                                .replaceWith(pair))
                        .flatMap(pair ->
                                postService(token(pair.getItem1())).publish(publication(pair.getItem2(), true))),
                Status.Code.NOT_FOUND,
                null);
    }

    private @Nonnull Uni<Tuple2<User, UUID>> createDraft(@Nonnull String username, @Nullable String chapterText) {
        return user(username)
                .flatMap(u ->
                        postService(token(u)).create(Empty.getDefaultInstance()).map(id -> Tuple2.of(u, uuid(id))))
                .flatMap(pair -> chapterText == null
                        ? Uni.createFrom().item(pair)
                        : addChapter(pair.getItem2(), chapterText).replaceWith(pair));
    }

    private @Nonnull Publication publication(@Nonnull UUID postId, boolean anonymous) {
        return Publication.newBuilder()
                .setId(toProtoService.byteString(postId))
                .setAnonymous(anonymous)
                .build();
    }
}
