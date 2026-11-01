package app.fyreplace.grpc.endpoints.comment;

import app.fyreplace.data.entities.Block;
import app.fyreplace.data.entities.Comment;
import app.fyreplace.grpc.endpoints.post.PostTestsBase;
import app.fyreplace.protos.Header;
import app.fyreplace.protos.MutinyCommentServiceGrpc;
import app.fyreplace.protos.Page;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class CommentTestsBase extends PostTestsBase {
    @Inject
    protected Block.Repository blockRepository;

    private List<UUID> seededCommentIds;

    @BeforeEach
    protected void snapshotComments() {
        executorService.awaitOnSafeContext(() -> commentRepository
                .findAll()
                .list()
                .invoke(list ->
                        seededCommentIds = list.stream().map(Comment::getId).toList()));
    }

    @AfterEach
    protected void cleanupComments() {
        executorService.awaitOnSafeContext(() -> commentRepository.deleteByIdNotIn(seededCommentIds));
    }

    protected final @Nonnull MutinyCommentServiceGrpc.MutinyCommentServiceStub commentService() {
        return MutinyCommentServiceGrpc.newMutinyStub(channel());
    }

    protected final @Nonnull MutinyCommentServiceGrpc.MutinyCommentServiceStub commentService(@Nullable String token) {
        return withToken(commentService(), token);
    }

    protected final @Nonnull Multi<Page> commentRequest(@Nonnull UUID postId, int size, int... indexes) {
        return commentRequest(postId, true, size, indexes);
    }

    protected final @Nonnull Multi<Page> commentRequest(
            @Nonnull UUID postId, boolean forward, int size, int... indexes) {
        final var pages = new ArrayList<Page>();
        pages.add(Page.newBuilder()
                .setHeader(Header.newBuilder()
                        .setForward(forward)
                        .setSize(size)
                        .setContextId(toProtoService.byteString(postId)))
                .build());
        for (final var index : indexes) {
            pages.add(Page.newBuilder()
                    .setCursor(toProtoService.cursor(new io.quarkus.panache.common.Page(index, size)))
                    .build());
        }
        return Multi.createFrom().items(pages.stream());
    }
}
