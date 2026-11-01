package app.fyreplace.grpc.endpoints.post;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.Post;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class DeleteTests extends PostTestsBase {
    @Test
    @RunOnVertxContext
    public void testDeleteOwnDraft(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .create(Empty.getDefaultInstance())
                                .map(id -> Tuple2.of(active, uuid(id))))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .delete(toProtoService.id(pair.getItem2()))
                                .replaceWith(pair))
                        .flatMap(pair -> session(() -> postRepository.findById(pair.getItem2()))),
                post -> {
                    assertTrue(post.isDeleted());
                    assertEquals(0, post.getLife());
                });
    }

    @Test
    @RunOnVertxContext
    public void testDeleteOwnPublished(@Nonnull UniAsserter asserter) {
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
                        .flatMap(pair -> transaction(() -> {
                            final var comment = new Comment();
                            comment.setAuthor(pair.getItem1());
                            final var post = new Post();
                            post.setId(pair.getItem2());
                            comment.setPost(post);
                            comment.setText("hi");
                            return commentRepository.persist(comment).replaceWith(pair);
                        }))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .delete(toProtoService.id(pair.getItem2()))
                                .replaceWith(pair))
                        .flatMap(pair -> Uni.combine()
                                .all()
                                .unis(
                                        session(() -> postRepository.findById(pair.getItem2())),
                                        session(() -> chapterRepository.countByPostId(pair.getItem2())),
                                        session(() -> commentRepository.countByPostId(pair.getItem2())))
                                .asTuple()),
                tuple -> {
                    assertTrue(tuple.getItem1().isDeleted());
                    assertEquals(0, tuple.getItem2());
                    assertEquals(0, tuple.getItem3());
                });
    }

    @Test
    @RunOnVertxContext
    public void testDeleteOthersAsCitizen(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .delete(toProtoService.id(pair.getItem2().getId()))),
                Status.Code.PERMISSION_DENIED,
                "invalid_post_author");
    }

    @Test
    @RunOnVertxContext
    public void testDeleteOthersAsStaff(@Nonnull UniAsserter asserter) {
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
                        .flatMap(pair -> user(STAFF_USER)
                                .flatMap(staff -> postService(token(staff))
                                        .delete(toProtoService.id(pair.getItem2()))
                                        .replaceWith(pair)))
                        .flatMap(pair -> session(() -> postRepository.findById(pair.getItem2()))),
                post -> assertTrue(post.isDeleted()));
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active)).delete(toProtoService.id(UUID.randomUUID()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postService().delete(toProtoService.id(UUID.randomUUID())),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testAlreadyDeleted(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .create(Empty.getDefaultInstance())
                                .map(id -> Tuple2.of(active, uuid(id))))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .delete(toProtoService.id(pair.getItem2()))
                                .replaceWith(pair))
                        .flatMap(
                                pair -> postService(token(pair.getItem1())).delete(toProtoService.id(pair.getItem2()))),
                Status.Code.NOT_FOUND,
                null);
    }
}
