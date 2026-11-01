package app.fyreplace.grpc.endpoints;

import app.fyreplace.errors.InvalidArgumentException;
import app.fyreplace.errors.NotFoundException;
import app.fyreplace.errors.PermissionDeniedException;
import app.fyreplace.protos.Id;
import app.fyreplace.protos.Page;
import app.fyreplace.protos.Post;
import app.fyreplace.protos.PostService;
import app.fyreplace.protos.Posts;
import app.fyreplace.protos.Publication;
import app.fyreplace.protos.Subscription;
import app.fyreplace.protos.Vote;
import app.fyreplace.services.AuthorizationService;
import app.fyreplace.services.ValidationService;
import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcService;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@GrpcService
public final class PostEndpointImpl extends EndpointBase implements PostService {
    private static final int FEED_WINDOW = 3;

    @Inject
    AuthorizationService authorizationService;

    @Inject
    ValidationService validationService;

    @Override
    public @Nonnull Multi<Post> listFeed(@Nonnull Multi<Vote> request) {
        final var callerId = currentUserService.getUserIdOrNull();
        return Multi.createFrom()
                .emitter(emitter -> Panache.withSession(
                                () -> postRepository.findFeed(callerId).list().flatMap(this::initialFeed))
                        .subscribe()
                        .with(
                                pair -> {
                                    pair.getItem2().forEach(emitter::emit);

                                    if (pair.getItem1().isEmpty()) {
                                        emitter.complete();
                                    } else {
                                        subscribeFeedVotes(request, emitter, callerId, pair.getItem1());
                                    }
                                },
                                emitter::fail));
    }

    @Override
    @Authenticated
    public @Nonnull Multi<Posts> listArchive(@Nonnull Multi<Page> request) {
        return paginate(
                request,
                (header, page) -> Panache.withSession(() -> notificationRepository
                        .findByUserId(currentUserService.getUserId(), header.getForward())
                        .page(page)
                        .list()
                        .flatMap(items -> toProtoService.notificationPosts(items, page))));
    }

    @Override
    @Authenticated
    public @Nonnull Multi<Posts> listOwnPosts(@Nonnull Multi<Page> request) {
        return paginate(
                request,
                (header, page) -> Panache.withSession(() -> postRepository
                        .findByAuthorId(currentUserService.getUserId(), true, header.getForward())
                        .page(page)
                        .list()
                        .flatMap(items -> toProtoService.posts(items, page, true))));
    }

    @Override
    @Authenticated
    public @Nonnull Multi<Posts> listDrafts(@Nonnull Multi<Page> request) {
        return paginate(
                request,
                (header, page) -> Panache.withSession(() -> postRepository
                        .findByAuthorId(currentUserService.getUserId(), false, header.getForward())
                        .page(page)
                        .list()
                        .flatMap(items -> toProtoService.posts(items, page, false))));
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Post> retrieve(@Nonnull Id request) {
        final var callerId = currentUserService.getUserId();
        return uuidOf(request::getId)
                .flatMap(id -> findReadablePostOrCommentPost(callerId, id))
                .call(post -> subscriptionRepository.updateLastSeenByUserIdAndPostId(callerId, post.getId()))
                .flatMap(toProtoService::post);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Id> create(@Nonnull Empty request) {
        return Uni.createFrom()
                .item(request)
                .replaceWith(fromProtoService::newPost)
                .flatMap(postRepository::persist)
                .map(app.fyreplace.data.entities.Post::getId)
                .map(toProtoService::id);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> publish(@Nonnull Publication request) {
        return uuidOf(request::getId)
                .flatMap(postId -> postRepository.findReadableById(currentUserService.getUserId(), postId))
                .invoke(post -> authorizationService.checkPostWritable(currentUserService.getUserId(), post))
                .call(post -> validationService.checkPostPublishable(post.getId()))
                .map(post -> Tuple2.of(post.getId(), request))
                .call(pair -> postRepository.updatePublishedById(
                        pair.getItem1(), pair.getItem2().getAnonymous()))
                .call(pair -> subscriptionRepository.persistIfNotExists(
                        fromProtoService.newSubscription(pair.getItem2().getId())))
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> delete(@Nonnull Id request) {
        return uuidOf(request::getId)
                .flatMap(postId -> postRepository.findReadableById(currentUserService.getUserId(), postId))
                .flatMap(post -> currentUserService.getUser().map(caller -> Tuple2.of(caller, post)))
                .invoke(pair -> authorizationService.checkPostAuthorOrStaff(pair.getItem1(), pair.getItem2()))
                .map(Tuple2::getItem2)
                .map(app.fyreplace.data.entities.Post::getId)
                .call(postRepository::softDeleteById)
                .call(chapterRepository::deleteByPostId)
                .call(commentRepository::deleteByPostId)
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> updateSubscription(@Nonnull Subscription request) {
        return uuidOf(request::getId)
                .flatMap(postId -> postRepository.findReadableById(currentUserService.getUserId(), postId))
                .invoke(authorizationService::checkPostPublished)
                .map(post -> Tuple2.of(post.getId(), request))
                .call(pair -> pair.getItem2().getSubscribed()
                        ? subscriptionRepository.persistIfNotExists(
                                fromProtoService.newSubscription(pair.getItem2().getId()))
                        : subscriptionRepository
                                .deleteByUserIdAndPostId(currentUserService.getUserId(), pair.getItem1())
                                .replaceWithVoid())
                .replaceWith(Empty::getDefaultInstance);
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> report(@Nonnull Id request) {
        final var callerId = currentUserService.getUserId();
        return uuidOf(request::getId)
                .flatMap(postId -> postRepository.findReadableById(callerId, postId))
                .invoke(authorizationService::checkPostPublished)
                .invoke(post -> authorizationService.checkPostReportable(callerId, post))
                .flatMap(post -> notifyStaffIfNewReport(
                        persistReportIfAbsent(
                                reportRepository.existsByIssuerIdAndTargetPostId(callerId, post.getId()),
                                fromProtoService.newPostReport(request.getId())),
                        Uni.createFrom().item(templateDataService.postFrontLink(post.getId()))));
    }

    @Override
    @Authenticated
    @WithTransaction
    public @Nonnull Uni<Empty> absolve(@Nonnull Id request) {
        return currentUserService
                .getUser()
                .invoke(authorizationService::checkStaff)
                .flatMap(caller -> uuidOf(request::getId)
                        .flatMap(postId -> postRepository.findReadableById(caller.getId(), postId))
                        .map(post -> Tuple2.of(caller, post)))
                .invoke(pair ->
                        authorizationService.checkPostAbsolvable(pair.getItem1().getId(), pair.getItem2()))
                .map(Tuple2::getItem2)
                .map(app.fyreplace.data.entities.Post::getId)
                .call(reportRepository::deleteByTargetId)
                .replaceWith(Empty::getDefaultInstance);
    }

    private void subscribeFeedVotes(
            @Nonnull Multi<Vote> request,
            @Nonnull MultiEmitter<? super Post> emitter,
            @Nullable UUID callerId,
            @Nonnull FeedState state) {
        final var done = new AtomicBoolean();
        request.concatMap(vote -> processFeedVote(vote, callerId, state))
                .subscribe()
                .with(
                        post -> {
                            emitter.emit(post);

                            if (state.isEmpty() && done.compareAndSet(false, true)) {
                                emitter.complete();
                            }
                        },
                        failure -> {
                            if (done.compareAndSet(false, true)) {
                                emitter.fail(failure);
                            }
                        },
                        () -> {
                            if (done.compareAndSet(false, true)) {
                                emitter.complete();
                            }
                        });
    }

    private @Nonnull Uni<Tuple2<FeedState, List<Post>>> initialFeed(
            @Nonnull List<app.fyreplace.data.entities.Post> candidates) {
        final var state = new FeedState(candidates);
        return state.initialPosts().collect().asList().map(posts -> Tuple2.of(state, posts));
    }

    private @Nonnull Multi<Post> processFeedVote(
            @Nonnull Vote vote, @Nullable UUID callerId, @Nonnull FeedState state) {
        final var postId = fromProtoService.uuid(vote.getPostId());

        if (!state.isInWindow(postId)) {
            return Multi.createFrom().failure(new InvalidArgumentException("post_not_in_feed"));
        }

        return castFeedVote(callerId, vote, postId)
                .replaceWith(postId)
                .invoke(state::remove)
                .invoke(state::refill)
                .onItem()
                .transformToMulti(_ -> state.nextPost()
                        .map(post -> Panache.withSession(() -> toProtoService.post(post))
                                .toMulti())
                        .orElseGet(() -> Multi.createFrom().empty()));
    }

    private @Nonnull Uni<Void> castFeedVote(@Nullable UUID callerId, @Nonnull Vote vote, @Nonnull UUID postId) {
        if (callerId == null) {
            return Uni.createFrom().voidItem();
        }

        return Panache.withTransaction(() -> voteRepository
                .findByUserIdAndPostId(callerId, postId)
                .flatMap(existing -> existing != null
                        ? Uni.createFrom().failure(new PermissionDeniedException("post_already_voted"))
                        : voteRepository
                                .castOrNull(fromProtoService.newVote(vote))
                                .replaceWithVoid()));
    }

    private @Nonnull Uni<app.fyreplace.data.entities.Post> findReadablePostOrCommentPost(
            @Nonnull UUID callerId, @Nonnull UUID id) {
        return postRepository
                .findReadableByIdOrNull(callerId, id)
                .flatMap(post -> post != null
                        ? Uni.createFrom().item(post)
                        : commentRepository
                                .findById(id)
                                .onItem()
                                .ifNull()
                                .failWith(NotFoundException::new)
                                .flatMap(comment -> postRepository.findReadableById(
                                        callerId, comment.getPost().getId())));
    }

    private final class FeedState {
        private final List<app.fyreplace.data.entities.Post> candidates;
        private final List<app.fyreplace.data.entities.Post> window = new ArrayList<>();
        private final Set<UUID> sentIds = new HashSet<>();

        FeedState(@Nonnull List<app.fyreplace.data.entities.Post> candidates) {
            this.candidates = candidates;
            candidates.stream().limit(FEED_WINDOW).forEach(this::track);
        }

        @Nonnull
        Multi<Post> initialPosts() {
            return Multi.createFrom()
                    .items(window.stream())
                    .onItem()
                    .transformToUni(toProtoService::post)
                    .concatenate();
        }

        boolean isInWindow(@Nonnull UUID postId) {
            return window.stream()
                    .limit(FEED_WINDOW)
                    .anyMatch(post -> post.getId().equals(postId));
        }

        boolean isEmpty() {
            return window.isEmpty();
        }

        void remove(@Nonnull UUID postId) {
            window.removeIf(post -> post.getId().equals(postId));
        }

        void refill() {
            for (final var candidate : candidates) {
                if (window.size() >= FEED_WINDOW) {
                    break;
                }

                track(candidate);
            }
        }

        @Nonnull
        Optional<app.fyreplace.data.entities.Post> nextPost() {
            return window.size() >= FEED_WINDOW ? Optional.of(window.get(FEED_WINDOW - 1)) : Optional.empty();
        }

        private void track(@Nonnull app.fyreplace.data.entities.Post post) {
            if (sentIds.add(post.getId())) {
                window.add(post);
            }
        }
    }
}
