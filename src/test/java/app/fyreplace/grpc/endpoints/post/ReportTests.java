package app.fyreplace.grpc.endpoints.post;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.fyreplace.grpc.endpoints.JwtTestsResource;
import app.fyreplace.protos.Publication;
import app.fyreplace.services.TemplateDataService;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(JwtTestsResource.class)
public final class ReportTests extends PostTestsBase {
    @Test
    @RunOnVertxContext
    public void testReport(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .report(toProtoService.id(pair.getItem2().getId()))
                                .replaceWith(pair))
                        .flatMap(pair -> session(() -> reportRepository.existsByIssuerIdAndTargetPostId(
                                pair.getItem1().getId(), pair.getItem2().getId()))),
                Assertions::assertTrue);
    }

    @Test
    @RunOnVertxContext
    public void testReportSendsEmailToStaff(@Nonnull UniAsserter asserter) {
        asserter.assertThat(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> deleteReport(
                                        pair.getItem1().getId(), pair.getItem2().getId())
                                .chain(() -> postService(token(pair.getItem1()))
                                        .report(toProtoService.id(
                                                pair.getItem2().getId())))
                                .replaceWith(pair))
                        .map(pair -> mailbox.getMailMessagesSentTo("staff@example.org").stream()
                                .anyMatch(mail -> mail.getHtml() != null
                                        && mail.getHtml()
                                                .contains("/p/"
                                                        + TemplateDataService.b64UrlId(
                                                                pair.getItem2().getId())))),
                Assertions::assertTrue);
    }

    @Test
    @RunOnVertxContext
    public void testReportTwice(@Nonnull UniAsserter asserter) {
        asserter.assertEquals(
                () -> joinAsTuple(user(OTHER_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> deleteReport(
                                        pair.getItem1().getId(), pair.getItem2().getId())
                                .chain(() -> postService(token(pair.getItem1()))
                                        .report(toProtoService.id(
                                                pair.getItem2().getId())))
                                .replaceWith(Empty.getDefaultInstance())
                                .chain(() -> postService(token(pair.getItem1()))
                                        .report(toProtoService.id(
                                                pair.getItem2().getId())))),
                Empty.getDefaultInstance());
        asserter.assertThat(
                () -> Uni.createFrom().item(mailbox.getMailMessagesSentTo("staff@example.org")),
                mails -> assertEquals(1, mails.size()));
    }

    @Test
    @RunOnVertxContext
    public void testReportOwnPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> joinAsTuple(user(ACTIVE_USER), postOf(ACTIVE_USER, true, 1))
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .report(toProtoService.id(pair.getItem2().getId()))),
                Status.Code.PERMISSION_DENIED,
                "invalid_post");
    }

    @Test
    @RunOnVertxContext
    public void testReportOwnDraft(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active))
                                .create(Empty.getDefaultInstance())
                                .flatMap(id -> postService(token(active)).report(toProtoService.id(uuid(id))))),
                Status.Code.PERMISSION_DENIED,
                "post_not_published");
    }

    @Test
    @RunOnVertxContext
    public void testNonExistent(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> user(ACTIVE_USER)
                        .flatMap(active -> postService(token(active)).report(toProtoService.id(UUID.randomUUID()))),
                Status.Code.NOT_FOUND,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testMissingCredentials(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
                () -> postService().report(toProtoService.id(UUID.randomUUID())),
                Status.Code.UNAUTHENTICATED,
                null);
    }

    @Test
    @RunOnVertxContext
    public void testDeletedPost(@Nonnull UniAsserter asserter) {
        assertFailure(
                asserter,
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
                        .flatMap(pair -> postService(token(pair.getItem1()))
                                .delete(toProtoService.id(pair.getItem2()))
                                .replaceWith(pair.getItem2()))
                        .flatMap(postId -> user(OTHER_USER)
                                .flatMap(other -> postService(token(other)).report(toProtoService.id(postId)))),
                Status.Code.NOT_FOUND,
                null);
    }

    private @Nonnull Uni<Void> deleteReport(@Nonnull UUID issuerId, @Nonnull UUID postId) {
        return transaction(() -> reportRepository.delete("issuer.id = ?1 AND targetPost.id = ?2", issuerId, postId))
                .replaceWithVoid();
    }
}
