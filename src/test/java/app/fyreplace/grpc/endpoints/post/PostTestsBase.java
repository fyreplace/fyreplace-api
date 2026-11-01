package app.fyreplace.grpc.endpoints.post;

import app.fyreplace.data.entities.Chapter;
import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.Report;
import app.fyreplace.data.entities.Subscription;
import app.fyreplace.data.entities.User;
import app.fyreplace.data.entities.Vote;
import app.fyreplace.grpc.GrpcTestsBase;
import app.fyreplace.protos.Header;
import app.fyreplace.protos.MutinyPostServiceGrpc;
import app.fyreplace.protos.Page;
import app.fyreplace.services.ToProtoService;
import io.quarkus.mailer.MockMailbox;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class PostTestsBase extends GrpcTestsBase {
    protected static final String ACTIVE_USER = "user_0";
    protected static final String OTHER_USER = "user_1";
    protected static final String TARGET_USER = "user_2";
    protected static final String STAFF_USER = "the_staff";

    @Inject
    protected Chapter.Repository chapterRepository;

    @Inject
    protected Comment.Repository commentRepository;

    @Inject
    protected Post.Repository postRepository;

    @Inject
    protected Report.Repository reportRepository;

    @Inject
    protected Subscription.Repository subscriptionRepository;

    @Inject
    protected Vote.Repository voteRepository;

    @Inject
    protected ToProtoService toProtoService;

    @Inject
    protected MockMailbox mailbox;

    private List<PostSnapshot> postSnapshots;
    private List<SubscriptionSnapshot> subscriptionSnapshots;
    private List<UUID> seededVoteIds;
    private List<UUID> seededReportIds;

    @BeforeEach
    protected void setup() {
        mailbox.clear();
        final var postsRef = new AtomicReference<List<Post>>();
        final var subscriptionsRef = new AtomicReference<List<Subscription>>();
        final var votesRef = new AtomicReference<List<Vote>>();
        final var reportsRef = new AtomicReference<List<Report>>();

        executorService.awaitOnSafeContext(() -> postRepository
                .findAll()
                .list()
                .invoke(postsRef::set)
                .chain(() -> subscriptionRepository.findAll().list())
                .invoke(subscriptionsRef::set)
                .chain(() -> voteRepository.findAll().list())
                .invoke(votesRef::set)
                .chain(() -> reportRepository.findAll().list())
                .invoke(reportsRef::set));

        postSnapshots = postsRef.get().stream().map(PostSnapshot::new).toList();
        subscriptionSnapshots =
                subscriptionsRef.get().stream().map(SubscriptionSnapshot::new).toList();
        seededVoteIds = votesRef.get().stream().map(Vote::getId).toList();
        seededReportIds = reportsRef.get().stream().map(Report::getId).toList();
    }

    @AfterEach
    protected void teardown() {
        executorService.awaitOnSafeContext(() -> Uni.createFrom()
                .voidItem()
                .chain(() -> postRepository.deleteByIdNotIn(
                        postSnapshots.stream().map(PostSnapshot::id).toList()))
                .chain(this::restorePosts)
                .chain(() -> subscriptionRepository.deleteByIdNotIn(subscriptionSnapshots.stream()
                        .map(SubscriptionSnapshot::id)
                        .toList()))
                .chain(this::restoreSubscriptions)
                .chain(() -> voteRepository.deleteByIdNotIn(seededVoteIds))
                .chain(() -> reportRepository.deleteByIdNotIn(seededReportIds))
                .chain(() -> userRepository.updateRankByUsername(ACTIVE_USER, User.Rank.CITIZEN))
                .chain(() -> userRepository.updateRankByUsername(OTHER_USER, User.Rank.CITIZEN))
                .chain(() -> userRepository.updateRankByUsername(STAFF_USER, User.Rank.STAFF)));
    }

    protected final @Nonnull MutinyPostServiceGrpc.MutinyPostServiceStub postService() {
        return MutinyPostServiceGrpc.newMutinyStub(channel());
    }

    protected final @Nonnull MutinyPostServiceGrpc.MutinyPostServiceStub postService(@Nullable String token) {
        return withToken(postService(), token);
    }

    protected final @Nonnull Uni<Post> postOf(@Nonnull String username, boolean published, int position) {
        return user(username)
                .flatMap(u -> session(() -> postRepository
                        .findByAuthorId(u.getId(), published, false)
                        .list()))
                .map(posts -> posts.get(position));
    }

    protected final @Nonnull Uni<Post> anyPostOf(@Nonnull String username, int position) {
        return user(username)
                .flatMap(u -> session(
                        () -> postRepository.findAllByAuthorId(u.getId()).list()))
                .map(posts -> posts.get(position));
    }

    protected final @Nonnull Uni<Comment> commentOf(@Nonnull Post post, int position) {
        return session(() -> commentRepository.findByPostId(post.getId(), true).list())
                .map(comments -> comments.get(position));
    }

    protected final @Nonnull Uni<Void> addChapter(@Nonnull UUID postId, @Nonnull String text) {
        return transaction(() -> {
            final var post = new Post();
            post.setId(postId);
            final var chapter = new Chapter();
            chapter.setPost(post);
            chapter.setPosition(0);
            chapter.setText(text);
            return chapterRepository.persist(chapter).replaceWithVoid();
        });
    }

    protected final @Nonnull Multi<Page> paginateRequest(int size, int... indexes) {
        return paginateRequest(false, size, indexes);
    }

    protected final @Nonnull Multi<Page> paginateRequest(boolean forward, int size, int... indexes) {
        final var pages = new ArrayList<Page>();
        pages.add(Page.newBuilder()
                .setHeader(Header.newBuilder().setForward(forward).setSize(size))
                .build());
        for (final var index : indexes) {
            pages.add(Page.newBuilder()
                    .setCursor(toProtoService.cursor(new io.quarkus.panache.common.Page(index, size)))
                    .build());
        }
        return Multi.createFrom().items(pages.stream());
    }

    private @Nonnull Uni<Void> restorePosts() {
        return chainAll(postSnapshots.stream()
                .map(snapshot -> (Supplier<Uni<Void>>) () -> postRepository
                        .restoreById(
                                snapshot.id(),
                                snapshot.isDeleted(),
                                snapshot.life(),
                                snapshot.isPublished(),
                                snapshot.anonymous(),
                                snapshot.dateCreated())
                        .replaceWithVoid())
                .toList());
    }

    private @Nonnull Uni<Void> restoreSubscriptions() {
        return chainAll(subscriptionSnapshots.stream()
                .map(snapshot -> (Supplier<Uni<Void>>) () -> restoreLastCommentSeen(snapshot)
                        .call(() -> subscriptionRepository
                                .updateLastSeenById(snapshot.id(), snapshot.dateLastSeen())
                                .replaceWithVoid()))
                .toList());
    }

    private @Nonnull Uni<Void> restoreLastCommentSeen(@Nonnull SubscriptionSnapshot snapshot) {
        return snapshot.lastCommentSeenId() == null
                ? subscriptionRepository
                        .update("lastCommentSeen = NULL WHERE id = ?1", snapshot.id())
                        .replaceWithVoid()
                : commentRepository
                        .findById(snapshot.lastCommentSeenId())
                        .call(comment -> subscriptionRepository
                                .update("lastCommentSeen = ?2 WHERE id = ?1", snapshot.id(), comment)
                                .replaceWithVoid())
                        .replaceWithVoid();
    }

    private record PostSnapshot(
            @Nonnull UUID id,
            boolean isDeleted,
            int life,
            boolean isPublished,
            boolean anonymous,
            @Nonnull OffsetDateTime dateCreated) {
        PostSnapshot(@Nonnull Post post) {
            this(
                    post.getId(),
                    post.isDeleted(),
                    post.getLife(),
                    post.isPublished(),
                    post.isAnonymous(),
                    post.getDateCreated());
        }
    }

    private record SubscriptionSnapshot(
            @Nonnull UUID id,
            @Nullable UUID lastCommentSeenId,
            @Nonnull OffsetDateTime dateLastSeen) {
        SubscriptionSnapshot(@Nonnull Subscription subscription) {
            this(
                    subscription.getId(),
                    subscription.getLastCommentSeen() != null
                            ? subscription.getLastCommentSeen().getId()
                            : null,
                    subscription.getDateLastSeen());
        }
    }
}
