package app.fyreplace.grpc.endpoints.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.data.entities.Chapter;
import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.MessagingToken;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.StoredFile;
import app.fyreplace.data.entities.User;
import app.fyreplace.grpc.endpoints.AccountTestsBase;
import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.ConnectionToken;
import app.fyreplace.protos.UserCreation;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class DeleteTests extends AccountTestsBase {
    private static final String VICTIM = "delete_victim";
    private static final String VICTIM_EMAIL = "delete_victim@example.org";
    private static final byte[] AVATAR = {1, 2, 3};

    @Inject
    StoredFile.Repository storedFileRepository;

    @Inject
    Post.Repository postRepository;

    @Inject
    Chapter.Repository chapterRepository;

    @Inject
    Comment.Repository commentRepository;

    @Inject
    MessagingToken.Repository messagingTokenRepository;

    @Test
    @RunOnVertxContext
    public void testActiveUserDeletion(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> newVictim(true)
                        .flatMap(victim -> accountService(token(victim))
                                .delete(Empty.getDefaultInstance())
                                .replaceWith(victim.getId()))
                        .flatMap(id -> session(() -> userRepository.findById(id))),
                victim -> {
                    assertTrue(victim.isDeleted());
                    assertNull(victim.getUsername());
                    assertNull(victim.getEmail());
                    assertFalse(victim.hasUsablePassword());
                    assertEquals("", victim.getBio());
                    assertNull(victim.getAvatar());
                });
    }

    @Test
    @RunOnVertxContext
    public void testPendingUserDeletion(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> newVictim(false)
                        .flatMap(victim -> accountService(token(victim))
                                .delete(Empty.getDefaultInstance())
                                .replaceWith(victim.getId()))
                        .flatMap(id -> session(() -> userRepository.findById(id))),
                victim -> {
                    assertTrue(victim.isDeleted());
                    assertNull(victim.getUsername());
                    assertNull(victim.getEmail());
                });
    }

    @Test
    @RunOnVertxContext
    public void testBannedUserDeletion(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> newVictim(true)
                        .call(victim -> session(() -> userRepository.updateBanByUserId(victim.getId(), 1))
                                .replaceWithVoid())
                        .flatMap(victim -> accountService(token(victim))
                                .delete(Empty.getDefaultInstance())
                                .replaceWith(victim.getId()))
                        .flatMap(id -> session(() -> userRepository.findById(id))),
                victim -> assertTrue(victim.isDeleted()));
    }

    @Test
    @RunOnVertxContext
    public void testDoubleDeletion(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> newVictim(true)
                        .flatMap(victim -> accountService(token(victim))
                                .delete(Empty.getDefaultInstance())
                                .replaceWith(victim)
                                .flatMap(user -> accountService(token(user)).delete(Empty.getDefaultInstance()))),
                Empty.getDefaultInstance());
    }

    @Test
    @RunOnVertxContext
    public void testAvatarDeletion(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> newVictim(true)
                        .flatMap(victim -> session(() -> userRepository.storeOrRemoveAvatarById(victim.getId(), AVATAR))
                                .flatMap(file -> accountService(token(victim))
                                        .delete(Empty.getDefaultInstance())
                                        .replaceWith(victim.getId())
                                        .map(userId -> Map.entry(userId, file.getId()))))
                        .flatMap(ids -> Uni.combine()
                                .all()
                                .unis(
                                        session(() -> userRepository.findById(ids.getKey())),
                                        session(() -> storedFileRepository.findById(ids.getValue())))
                                .asTuple()),
                ids -> {
                    assertTrue(ids.getItem1().isDeleted());
                    assertNull(ids.getItem1().getAvatar());
                    assertNull(ids.getItem2());
                });
    }

    @Test
    @RunOnVertxContext
    public void testUserContentRemoval(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> newVictim(true)
                        .flatMap(victim -> transaction(() -> {
                            final var post = new Post();
                            post.setAuthor(victim);
                            return postRepository.persist(post).flatMap(persisted -> {
                                final var chapter = new Chapter();
                                chapter.setPost(persisted);
                                chapter.setPosition(0);
                                chapter.setText("hello");
                                final var comment = new Comment();
                                comment.setAuthor(victim);
                                comment.setPost(persisted);
                                comment.setText("hi");
                                final var token = new MessagingToken();
                                token.setUser(victim);
                                token.setService(MessagingToken.Service.APNS);
                                token.setToken("delete-victim-token");
                                return Uni.combine()
                                        .all()
                                        .unis(
                                                chapterRepository.persist(chapter),
                                                commentRepository.persist(comment),
                                                messagingTokenRepository.persist(token))
                                        .asTuple()
                                        .replaceWith(persisted.getId());
                            });
                        }))
                        .flatMap(postId -> session(() -> userRepository.findByUsername(VICTIM))
                                .flatMap(victim -> accountService(token(victim))
                                        .delete(Empty.getDefaultInstance())
                                        .replaceWith(Tuple2.of(victim.getId(), postId))))
                        .flatMap(pair -> Uni.combine()
                                .all()
                                .unis(
                                        session(() -> postRepository.findById(pair.getItem2())),
                                        session(() -> chapterRepository.countByPostId(pair.getItem2())),
                                        session(() -> commentRepository.countByPostId(pair.getItem2())),
                                        session(() -> messagingTokenRepository
                                                .findByUserId(pair.getItem1())
                                                .list()))
                                .asTuple()),
                tuple -> {
                    assertTrue(tuple.getItem1().isDeleted());
                    assertEquals(0, tuple.getItem2());
                    assertEquals(0, tuple.getItem3());
                    assertTrue(tuple.getItem4().isEmpty());
                });
    }

    @Test
    @RunOnVertxContext
    public void testUnknownCaller(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService(token(unknownCaller())).delete(Empty.getDefaultInstance()),
                Status.Code.UNAUTHENTICATED,
                "invalid_token");
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter, () -> accountService().delete(Empty.getDefaultInstance()), Status.Code.UNAUTHENTICATED, null);
    }

    @Test
    @RunOnVertxContext
    public void testInvalidBearerToken(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> accountService("invalid").delete(Empty.getDefaultInstance()),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    private @Nonnull Uni<User> newVictim(boolean active) {
        return accountService()
                .create(UserCreation.newBuilder()
                        .setEmail(VICTIM_EMAIL)
                        .setUsername(VICTIM)
                        .build())
                .replaceWith(VICTIM)
                .flatMap(this::user)
                .call(victim -> active
                        ? accountService()
                                .confirmActivation(ConnectionToken.newBuilder()
                                        .setToken(jwtService.makeJwt(victim, true))
                                        .build())
                        : Uni.createFrom().voidItem());
    }

    private static @Nonnull User unknownCaller() {
        final var caller = new User();
        caller.setId(UUID.randomUUID());
        caller.setUsername("unknown");
        return caller;
    }
}
