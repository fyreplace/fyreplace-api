package app.fyreplace.grpc.endpoints;

import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.Post;
import app.fyreplace.errors.NotFoundException;
import app.fyreplace.protos.CommentCreation;
import app.fyreplace.protos.CommentService;
import app.fyreplace.protos.Comments;
import app.fyreplace.protos.Id;
import app.fyreplace.protos.Page;
import app.fyreplace.services.AuthorizationService;
import app.fyreplace.services.ValidationService;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.util.UUID;

@GrpcService
public final class CommentEndpointImpl extends EndpointBase implements CommentService {
    @Inject
    AuthorizationService authorizationService;

    @Inject
    ValidationService validationService;

    @Override
    public @Nonnull Multi<Comments> list(@Nonnull Multi<Page> request) {
        final var callerId = currentUserService.getUserIdOrNull();
        return paginate(
                request,
                true,
                (header, page) -> Panache.withSession(() -> findListablePost(callerId, header.getContextId())
                        .flatMap(post -> commentRepository
                                .findByPostId(post.getId(), header.getForward())
                                .page(page)
                                .list()
                                .flatMap(items -> toProtoService.comments(items, page))
                                .flatMap(comments -> Uni.createFrom()
                                        .item(post::getId)
                                        .flatMap(commentRepository::countByPostId)
                                        .map(count -> comments.toBuilder()
                                                .setCount(count.intValue())
                                                .build())))));
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Id> create(@Nonnull CommentCreation request) {
        final var callerId = currentUserService.getUserId();
        return uuidOf(request::getPostId)
                .flatMap(postId -> postRepository.findReadableById(callerId, postId))
                .invoke(authorizationService::checkPostPublished)
                .call(post ->
                        authorizationService.checkNotBlockedBy(post.getAuthor().getId(), callerId))
                .replaceWith(request::getText)
                .call(validationService::checkCommentText)
                .replaceWith(request)
                .map(fromProtoService::newComment)
                .call(commentRepository::persistAndFlush)
                .call(comment -> Uni.createFrom()
                        .item(comment::getPost)
                        .map(Post::getId)
                        .map(fromProtoService::newSubscription)
                        .flatMap(subscriptionRepository::persistIfNotExists))
                .call(comment -> subscriptionRepository.updateLastCommentSeenByUserId(
                        comment.getAuthor().getId(), comment))
                .map(Comment::getId)
                .map(toProtoService::id);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> delete(@Nonnull Id request) {
        return uuidOf(request::getId)
                .flatMap(this::findCommentOrNotFound)
                .flatMap(comment -> currentUserService.getUser().map(caller -> Tuple2.of(caller, comment)))
                .invoke(pair -> authorizationService.checkCommentAuthorOrStaff(pair.getItem1(), pair.getItem2()))
                .map(Tuple2::getItem2)
                .map(Comment::getId)
                .call(commentRepository::softDeleteById)
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> acknowledge(@Nonnull Id request) {
        return uuidOf(request::getId)
                .flatMap(this::findCommentOrNotFound)
                .call(comment ->
                        subscriptionRepository.updateLastCommentSeenByUserId(currentUserService.getUserId(), comment))
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> report(@Nonnull Id request) {
        final var callerId = currentUserService.getUserId();
        return uuidOf(request::getId)
                .flatMap(this::findCommentOrNotFound)
                .invoke(comment -> authorizationService.checkCommentReportable(callerId, comment))
                .flatMap(comment -> notifyStaffIfNewReport(
                        persistReportIfAbsent(
                                reportRepository.existsByIssuerIdAndTargetCommentId(callerId, comment.getId()),
                                fromProtoService.newCommentReport(request.getId())),
                        templateDataService.commentFrontLink(comment)));
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> absolve(@Nonnull Id request) {
        return currentUserService
                .getUser()
                .invoke(authorizationService::checkStaff)
                .flatMap(caller -> uuidOf(request::getId)
                        .flatMap(commentRepository::findExistingById)
                        .map(comment -> Tuple2.of(caller, comment)))
                .invoke(pair -> authorizationService.checkCommentAbsolvable(
                        pair.getItem1().getId(), pair.getItem2()))
                .map(Tuple2::getItem2)
                .map(Comment::getId)
                .call(reportRepository::deleteByTargetId)
                .replaceWith(Empty::getDefaultInstance);
    }

    private @Nonnull Uni<Comment> findCommentOrNotFound(@Nonnull UUID id) {
        return commentRepository.findById(id).onItem().ifNull().failWith(NotFoundException::new);
    }

    private @Nonnull Uni<app.fyreplace.data.entities.Post> findListablePost(
            @Nullable UUID callerId, @Nonnull ByteString contextId) {
        return postRepository
                .findReadableByIdOrNull(callerId, fromProtoService.uuid(contextId))
                .onItem()
                .ifNull()
                .failWith(NotFoundException::new)
                .invoke(authorizationService::checkPostPublished);
    }
}
