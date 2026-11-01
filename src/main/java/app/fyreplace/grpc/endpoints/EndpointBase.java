package app.fyreplace.grpc.endpoints;

import app.fyreplace.data.entities.Block;
import app.fyreplace.data.entities.Chapter;
import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.MessagingToken;
import app.fyreplace.data.entities.Notification;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.Report;
import app.fyreplace.data.entities.Subscription;
import app.fyreplace.data.entities.User;
import app.fyreplace.data.entities.Vote;
import app.fyreplace.errors.InvalidArgumentException;
import app.fyreplace.mails.StaffReportMail;
import app.fyreplace.protos.Header;
import app.fyreplace.protos.Page;
import app.fyreplace.services.CurrentUserService;
import app.fyreplace.services.FromProtoService;
import app.fyreplace.services.TemplateDataService;
import app.fyreplace.services.ToProtoService;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;
import io.vertx.core.Vertx;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jboss.logging.Logger;

public abstract class EndpointBase {
    private static final Logger LOG = Logger.getLogger(EndpointBase.class);
    private static final int PAGE_SIZE_MAX = 50;

    @Inject
    Block.Repository blockRepository;

    @Inject
    Chapter.Repository chapterRepository;

    @Inject
    Comment.Repository commentRepository;

    @Inject
    MessagingToken.Repository messagingTokenRepository;

    @Inject
    Notification.Repository notificationRepository;

    @Inject
    Post.Repository postRepository;

    @Inject
    Report.Repository reportRepository;

    @Inject
    Subscription.Repository subscriptionRepository;

    @Inject
    User.Repository userRepository;

    @Inject
    Vote.Repository voteRepository;

    @Inject
    FromProtoService fromProtoService;

    @Inject
    ToProtoService toProtoService;

    @Inject
    CurrentUserService currentUserService;

    @Inject
    StaffReportMail staffReportMail;

    @Inject
    TemplateDataService templateDataService;

    @Inject
    Vertx vertx;

    protected @Nonnull Executor eventLoop() {
        return command -> vertx.getOrCreateContext().runOnContext(_ -> command.run());
    }

    protected @Nonnull Uni<UUID> uuidOf(@Nonnull Supplier<ByteString> id) {
        return Uni.createFrom().item(id).map(fromProtoService::uuid);
    }

    protected <I, B> @Nonnull Multi<B> paginate(
            @Nonnull Multi<Page> request,
            @Nonnull Function<Header, PanacheQuery<I>> findItems,
            @Nonnull BiFunction<List<I>, io.quarkus.panache.common.Page, Uni<B>> makeBundle) {
        return paginate(
                request,
                (header, page) ->
                        findItems.apply(header).page(page).list().flatMap(items -> makeBundle.apply(items, page)));
    }

    protected <B> @Nonnull Multi<B> paginate(
            @Nonnull Multi<Page> request,
            @Nonnull BiFunction<Header, io.quarkus.panache.common.Page, Uni<B>> processPage) {
        return paginate(request, false, processPage);
    }

    protected <B> @Nonnull Multi<B> paginate(
            @Nonnull Multi<Page> request,
            boolean randomAccess,
            @Nonnull BiFunction<Header, io.quarkus.panache.common.Page, Uni<B>> processPage) {
        final var cached = request.cache();
        final var headerRef = new AtomicReference<Header>();
        return cached.concatMap(page -> toPageRequest(headerRef, randomAccess, page))
                .onItem()
                .transformToUniAndConcatenate(pair -> processPage.apply(pair.getItem1(), pair.getItem2()));
    }

    protected @Nonnull Uni<Boolean> persistReportIfAbsent(
            @Nonnull Uni<Boolean> alreadyReported, @Nonnull Report report) {
        return alreadyReported.flatMap(exists -> exists
                ? Uni.createFrom().item(false)
                : reportRepository.persist(report).replaceWith(true));
    }

    protected @Nonnull Uni<Empty> notifyStaffIfNewReport(@Nonnull Uni<Boolean> created, @Nonnull Uni<URI> link) {
        return created.flatMap(wasCreated -> wasCreated
                ? link.call(staffReportMail::sendToStaff)
                        .onFailure()
                        .invoke(failure -> LOG.error("Failed to send report email to staff", failure))
                        .onFailure()
                        .recoverWithNull()
                        .replaceWith(Empty.getDefaultInstance())
                : Uni.createFrom().item(Empty.getDefaultInstance()));
    }

    private @Nonnull Multi<Tuple2<Header, io.quarkus.panache.common.Page>> toPageRequest(
            @Nonnull AtomicReference<Header> headerRef, boolean randomAccess, @Nonnull Page page) {
        if (page.hasHeader()) {
            checkPageSize(page.getHeader());
            headerRef.set(page.getHeader());
            return Multi.createFrom().empty();
        }

        final var header = headerRef.get();

        if (header == null) {
            return Multi.createFrom().failure(new InvalidArgumentException("missing_header"));
        }

        if (page.hasCursor()) {
            return Multi.createFrom().item(Tuple2.of(header, fromProtoService.page(header, page.getCursor())));
        }

        if (page.hasOffset() && randomAccess) {
            return Multi.createFrom()
                    .item(Tuple2.of(
                            header,
                            io.quarkus.panache.common.Page.ofSize(header.getSize())
                                    .index(page.getOffset() / header.getSize())));
        }

        return Multi.createFrom().failure(new InvalidArgumentException("random_access_unauthorized"));
    }

    private static void checkPageSize(@Nonnull Header header) {
        if (header.getSize() <= 0 || header.getSize() > PAGE_SIZE_MAX) {
            throw new InvalidArgumentException("invalid_size");
        }
    }
}
