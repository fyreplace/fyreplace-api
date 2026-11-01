package app.fyreplace.grpc.endpoints.notification;

import app.fyreplace.data.entities.MessagingToken;
import app.fyreplace.grpc.endpoints.post.PostTestsBase;
import app.fyreplace.protos.MutinyNotificationServiceGrpc;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class NotificationTestsBase extends PostTestsBase {
    @Inject
    protected MessagingToken.Repository messagingTokenRepository;

    private List<UUID> seededTokenIds;
    private List<UUID> seededCommentIds;

    @BeforeEach
    protected void snapshotTokens() {
        executorService.awaitOnSafeContext(() -> messagingTokenRepository
                .findAll()
                .list()
                .invoke(list -> seededTokenIds =
                        list.stream().map(MessagingToken::getId).toList())
                .chain(() -> commentRepository.findAll().list())
                .invoke(list -> seededCommentIds = list.stream()
                        .map(app.fyreplace.data.entities.Comment::getId)
                        .toList()));
    }

    @AfterEach
    protected void cleanupTokens() {
        executorService.awaitOnSafeContext(() -> messagingTokenRepository.deleteByIdNotIn(seededTokenIds));
    }

    @AfterEach
    protected void cleanupComments() {
        executorService.awaitOnSafeContext(() -> commentRepository.deleteByIdNotIn(seededCommentIds));
    }

    protected final @Nonnull MutinyNotificationServiceGrpc.MutinyNotificationServiceStub notificationService() {
        return MutinyNotificationServiceGrpc.newMutinyStub(channel());
    }

    protected final @Nonnull MutinyNotificationServiceGrpc.MutinyNotificationServiceStub notificationService(
            @Nullable String token) {
        return withToken(notificationService(), token);
    }

    protected final @Nonnull app.fyreplace.protos.MutinyCommentServiceGrpc.MutinyCommentServiceStub commentService(
            @Nullable String token) {
        return withToken(app.fyreplace.protos.MutinyCommentServiceGrpc.newMutinyStub(channel()), token);
    }
}
