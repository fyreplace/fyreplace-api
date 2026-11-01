package app.fyreplace.grpc.endpoints;

import app.fyreplace.errors.AlreadyExistsException;
import app.fyreplace.protos.MessagingToken;
import app.fyreplace.protos.NotificationCount;
import app.fyreplace.protos.NotificationService;
import app.fyreplace.protos.Notifications;
import app.fyreplace.protos.Page;
import app.fyreplace.services.ValidationService;
import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

@GrpcService
public final class NotificationEndpointImpl extends EndpointBase implements NotificationService {
    @Inject
    ValidationService validationService;

    @Override
    @Authenticated
    @WithSession
    public @Nonnull Uni<NotificationCount> count(@Nonnull Empty request) {
        return Uni.createFrom()
                .item(currentUserService::getUserId)
                .flatMap(notificationRepository::sumUnreadCommentCountByUserId)
                .map(toProtoService::notificationCount);
    }

    @Override
    @Authenticated
    public @Nonnull Multi<Notifications> list(@Nonnull Multi<Page> request) {
        return paginate(
                request,
                (header, page) -> Panache.withSession(() -> notificationRepository
                        .findByUserId(currentUserService.getUserId(), header.getForward())
                        .page(page)
                        .list()
                        .flatMap(items -> toProtoService.notifications(items, page))));
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> clear(@Nonnull Empty request) {
        return Uni.createFrom()
                .item(currentUserService::getUserId)
                .call(subscriptionRepository::markAllReadByUserId)
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> registerToken(@Nonnull MessagingToken request) {
        final var callerId = currentUserService.getUserId();
        return Uni.createFrom()
                .item(request)
                .call(validationService::checkMessagingToken)
                .flatMap(r -> messagingTokenRepository
                        .findByTokenOrNull(r.getToken())
                        .flatMap(existing ->
                                existing != null && !existing.getUser().getId().equals(callerId)
                                        ? Uni.createFrom().failure(new AlreadyExistsException("token_taken"))
                                        : Uni.createFrom().voidItem())
                        .replaceWith(r))
                .replaceWith(callerId)
                .call(messagingTokenRepository::deleteByUserId)
                .replaceWith(request)
                .map(fromProtoService::newMessagingToken)
                .call(messagingTokenRepository::persist)
                .replaceWith(Empty::getDefaultInstance);
    }
}
