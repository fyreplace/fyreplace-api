package app.fyreplace.grpc.endpoints.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.fyreplace.data.entities.Block;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.User;
import app.fyreplace.data.entities.Vote;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Publication;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class ListFeedTests extends PostTestsBase {
    @Inject
    Block.Repository blockRepository;

    @Test
    @RunOnVertxContext
    public void testInitialBatch(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createPublishedPosts(OTHER_USER, 4)
                        .flatMap(ids -> user(ACTIVE_USER)
                                .flatMap(active -> postService(token(active))
                                        .listFeed(Multi.createFrom().empty())
                                        .collect()
                                        .asList()
                                        .map(posts -> Tuple2.of(ids, posts)))),
                pair -> {
                    assertEquals(3, pair.getItem2().size());
                    assertEquals(
                            pair.getItem1().subList(0, 3),
                            pair.getItem2().stream()
                                    .map(post -> fromProtoService.uuid(post.getId()))
                                    .toList());
                });
    }

    @Test
    @RunOnVertxContext
    public void testVoteYieldsNextPost(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createPublishedPosts(OTHER_USER, 4)
                        .flatMap(ids -> user(ACTIVE_USER)
                                .flatMap(active -> postService(token(active))
                                        .listFeed(Multi.createFrom().items(vote(ids.getFirst(), true)))
                                        .collect()
                                        .asList()
                                        .map(posts -> Tuple2.of(ids, posts)))),
                pair -> assertEquals(
                        pair.getItem1(),
                        pair.getItem2().stream()
                                .map(post -> fromProtoService.uuid(post.getId()))
                                .toList()));
    }

    @Test
    @RunOnVertxContext
    public void testVoteIsCast(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createPublishedPosts(OTHER_USER, 1)
                        .flatMap(ids -> user(ACTIVE_USER)
                                .flatMap(active -> postService(token(active))
                                        .listFeed(Multi.createFrom().items(vote(ids.getFirst(), true)))
                                        .collect()
                                        .asList()
                                        .replaceWith(Tuple2.of(active, ids))))
                        .flatMap(pair -> session(() -> voteRepository.findByUserIdAndPostId(
                                        pair.getItem1().getId(), pair.getItem2().getFirst()))
                                .flatMap(v -> session(() -> postRepository.findById(
                                                pair.getItem2().getFirst()))
                                        .map(post -> Tuple2.of(v, post)))),
                pair -> {
                    assertNotNull(pair.getItem1());
                    assertEquals(
                            Post.INITIAL_LIFE + Vote.SPREAD_LIFE,
                            pair.getItem2().getLife());
                });
    }

    @Test
    @RunOnVertxContext
    public void testVoteNotInFeed(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> createPublishedPosts(OTHER_USER, 2)
                        .flatMap(ids -> user(ACTIVE_USER)
                                .flatMap(active -> postService(token(active))
                                        .listFeed(Multi.createFrom().items(vote(UUID.randomUUID(), true)))
                                        .collect()
                                        .asList())),
                Status.Code.INVALID_ARGUMENT,
                "post_not_in_feed");
    }

    @Test
    @RunOnVertxContext
    public void testVoteOutsideWindow(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> createPublishedPosts(OTHER_USER, 4)
                        .flatMap(ids -> user(ACTIVE_USER)
                                .flatMap(active -> postService(token(active))
                                        .listFeed(Multi.createFrom().items(vote(ids.get(3), true)))
                                        .collect()
                                        .asList())),
                Status.Code.INVALID_ARGUMENT,
                "post_not_in_feed");
    }

    @Test
    @RunOnVertxContext
    public void testExhaustedFeedCompletes(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createPublishedPosts(OTHER_USER, 3)
                        .flatMap(ids -> user(ACTIVE_USER)
                                .flatMap(active -> postService(token(active))
                                        .listFeed(Multi.createFrom()
                                                .items(
                                                        vote(ids.get(0), false),
                                                        vote(ids.get(1), false),
                                                        vote(ids.get(2), false)))
                                        .collect()
                                        .asList()
                                        .map(posts -> Tuple2.of(ids, posts)))),
                pair -> assertEquals(
                        pair.getItem1(),
                        pair.getItem2().stream()
                                .map(post -> fromProtoService.uuid(post.getId()))
                                .toList()));
    }

    @Test
    @RunOnVertxContext
    public void testAnonymousFeedIgnoresVotes(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createPublishedPosts(OTHER_USER, 3)
                        .flatMap(ids -> session(() -> voteRepository.count())
                                .flatMap(countBefore -> postService()
                                        .listFeed(Multi.createFrom().items(vote(ids.getFirst(), true)))
                                        .collect()
                                        .asList()
                                        .flatMap(posts -> session(() -> voteRepository.count())
                                                .map(countAfter -> Tuple2.of(
                                                        Tuple2.of(ids, posts), Tuple2.of(countBefore, countAfter)))))),
                pair -> {
                    assertEquals(3, pair.getItem1().getItem2().size());
                    assertEquals(pair.getItem2().getItem1(), pair.getItem2().getItem2());
                });
    }

    @Test
    @RunOnVertxContext
    public void testOwnPostsExcluded(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createPublishedPosts(ACTIVE_USER, 1)
                        .flatMap(own -> createPublishedPosts(OTHER_USER, 2)
                                .flatMap(others -> user(ACTIVE_USER)
                                        .flatMap(active -> postService(token(active))
                                                .listFeed(Multi.createFrom().empty())
                                                .collect()
                                                .asList()
                                                .map(posts -> Tuple2.of(Tuple2.of(own, others), posts))))),
                pair -> {
                    final var ids = pair.getItem2().stream()
                            .map(post -> fromProtoService.uuid(post.getId()))
                            .toList();
                    assertEquals(pair.getItem1().getItem2(), ids);
                    assertFalse(ids.contains(pair.getItem1().getItem1().getFirst()));
                });
    }

    @Test
    @RunOnVertxContext
    public void testEmptyFeed(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .listFeed(Multi.createFrom().empty())
                                .collect()
                                .asList()),
                posts -> assertEquals(0, posts.size()));
    }

    @Test
    @RunOnVertxContext
    public void testDeletedPostExcluded(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createPublishedPosts(OTHER_USER, 3)
                        .flatMap(ids -> user(OTHER_USER)
                                .flatMap(other -> postService(token(other))
                                        .delete(toProtoService.id(ids.get(1)))
                                        .replaceWith(ids)))
                        .flatMap(ids -> feedIds(ids)),
                pair -> {
                    assertEquals(2, pair.getItem2().size());
                    assertFalse(pair.getItem2().contains(pair.getItem1().get(1)));
                });
    }

    @Test
    @RunOnVertxContext
    public void testOldPostExcluded(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createPublishedPosts(OTHER_USER, 3)
                        .flatMap(ids -> session(() -> postRepository.restoreById(
                                        ids.get(1),
                                        false,
                                        Post.INITIAL_LIFE,
                                        true,
                                        false,
                                        OffsetDateTime.now().minusDays(8)))
                                .replaceWith(ids))
                        .flatMap(this::feedIds),
                pair -> {
                    assertEquals(2, pair.getItem2().size());
                    assertFalse(pair.getItem2().contains(pair.getItem1().get(1)));
                });
    }

    @Test
    @RunOnVertxContext
    public void testDeadPostExcluded(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createPublishedPosts(OTHER_USER, 3)
                        .flatMap(ids -> session(() -> postRepository.restoreById(
                                        ids.get(1), false, 0, true, false, OffsetDateTime.now()))
                                .replaceWith(ids))
                        .flatMap(this::feedIds),
                pair -> {
                    assertEquals(2, pair.getItem2().size());
                    assertFalse(pair.getItem2().contains(pair.getItem1().get(1)));
                });
    }

    @Test
    @RunOnVertxContext
    public void testDraftExcluded(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(OTHER_USER)
                        .flatMap(other -> postService(token(other))
                                .create(Empty.getDefaultInstance())
                                .map(id -> Tuple2.of(other, uuid(id))))
                        .flatMap(pair ->
                                addChapter(pair.getItem2(), "draft chapter").replaceWith(pair.getItem2()))
                        .flatMap(draftId -> createPublishedPosts(OTHER_USER, 2)
                                .flatMap(ids -> feedIds(ids).map(feed -> Tuple2.of(draftId, feed.getItem2())))),
                pair -> {
                    assertEquals(2, pair.getItem2().size());
                    assertFalse(pair.getItem2().contains(pair.getItem1()));
                });
    }

    @Test
    @RunOnVertxContext
    public void testBlockedAuthorExcluded(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> createPublishedPosts(OTHER_USER, 2)
                        .chain(() -> joinAsTuple(user(ACTIVE_USER), user(OTHER_USER)))
                        .flatMap(this::blockEachOther)
                        .flatMap(users -> postService(token(users.getItem1()))
                                .listFeed(Multi.createFrom().empty())
                                .collect()
                                .asList()
                                .map(posts -> Tuple2.of(users, posts)))
                        .call(pair -> unblockEachOther(pair.getItem1())),
                pair -> assertEquals(0, pair.getItem2().size()));
    }

    private @Nonnull Uni<Tuple2<User, User>> blockEachOther(@Nonnull Tuple2<User, User> users) {
        final var block = new Block();
        block.setIssuer(users.getItem1());
        block.setTarget(users.getItem2());
        return transaction(() -> blockRepository.persist(block)).replaceWith(users);
    }

    private @Nonnull Uni<Void> unblockEachOther(@Nonnull Tuple2<User, User> users) {
        return transaction(() -> blockRepository.deleteByIssuerIdAndTargetId(
                        users.getItem1().getId(), users.getItem2().getId()))
                .replaceWithVoid();
    }

    private @Nonnull Uni<Tuple2<List<UUID>, List<UUID>>> feedIds(@Nonnull List<UUID> ids) {
        return user(ACTIVE_USER)
                .flatMap(active -> postService(token(active))
                        .listFeed(Multi.createFrom().empty())
                        .collect()
                        .asList()
                        .map(posts -> Tuple2.of(
                                ids,
                                posts.stream()
                                        .map(post -> fromProtoService.uuid(post.getId()))
                                        .toList())));
    }

    private @Nonnull app.fyreplace.protos.Vote vote(@Nonnull UUID postId, boolean spread) {
        return app.fyreplace.protos.Vote.newBuilder()
                .setPostId(toProtoService.byteString(postId))
                .setSpread(spread)
                .build();
    }

    private @Nonnull Uni<UUID> createPublishedPost(@Nonnull String username) {
        return user(username)
                .flatMap(u ->
                        postService(token(u)).create(Empty.getDefaultInstance()).map(id -> Tuple2.of(u, uuid(id))))
                .flatMap(pair -> addChapter(pair.getItem2(), "chapter").replaceWith(pair))
                .flatMap(pair -> postService(token(pair.getItem1()))
                        .publish(Publication.newBuilder()
                                .setId(toProtoService.byteString(pair.getItem2()))
                                .build())
                        .replaceWith(pair.getItem2()));
    }

    private @Nonnull Uni<List<UUID>> createPublishedPosts(@Nonnull String username, int count) {
        Uni<List<UUID>> uni = Uni.createFrom().item(new ArrayList<UUID>());
        for (var i = 0; i < count; i++) {
            uni = uni.flatMap(
                    ids -> createPublishedPost(username).invoke(ids::add).replaceWith(ids));
        }
        return uni;
    }
}
