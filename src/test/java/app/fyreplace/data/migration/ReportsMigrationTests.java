package app.fyreplace.data.migration;

import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.Report;
import app.fyreplace.data.entities.User;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@RunOnVertxContext
public final class ReportsMigrationTests extends MigrationTestsBase {
    @Test
    void testEntityCount(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertEquals(reportRepository::count, 3L);
    }

    @Test
    void testUserReport(@Nonnull TransactionalUniAsserter asserter) {
        final Supplier<Uni<Report>> reportSupplier = () -> userRepository
                .findByUsername("user_2")
                .map(User::getId)
                .map(reportRepository::findByIssuerId)
                .flatMap(PanacheQuery::singleResult);
        final Supplier<Uni<User>> targetSupplier = () -> userRepository.findByUsername("user_0");

        asserter.assertThat(() -> joinAsTuple(reportSupplier.get(), targetSupplier.get()), tuple -> {
            Assertions.assertNotNull(tuple.getItem1().getTargetUser());
            Assertions.assertNull(tuple.getItem1().getTargetPost());
            Assertions.assertNull(tuple.getItem1().getTargetComment());
            Assertions.assertEquals(
                    tuple.getItem2().getId(), tuple.getItem1().getTargetUser().getId());
        });
    }

    @Test
    void testPostReport(@Nonnull TransactionalUniAsserter asserter) {
        final Supplier<Uni<Report>> reportSupplier = () -> userRepository
                .findByUsername("user_1")
                .map(User::getId)
                .map(reportRepository::findByIssuerId)
                .flatMap(PanacheQuery::singleResult);
        final Supplier<Uni<Post>> targetSupplier =
                () -> getPostsFromUser("user_0").map(List::getLast);

        asserter.assertThat(() -> joinAsTuple(reportSupplier.get(), targetSupplier.get()), tuple -> {
            Assertions.assertNull(tuple.getItem1().getTargetUser());
            Assertions.assertNotNull(tuple.getItem1().getTargetPost());
            Assertions.assertNull(tuple.getItem1().getTargetComment());
            Assertions.assertEquals(
                    tuple.getItem2().getId(), tuple.getItem1().getTargetPost().getId());
        });
    }

    @Test
    void testCommentReport(@Nonnull TransactionalUniAsserter asserter) {
        final Supplier<Uni<Report>> reportSupplier = () -> userRepository
                .findByUsername("user_0")
                .map(User::getId)
                .map(reportRepository::findByIssuerId)
                .flatMap(PanacheQuery::singleResult);
        final Supplier<Uni<Comment>> targetSupplier = () -> userRepository
                .findByUsername("user_1")
                .map(User::getId)
                .map(commentRepository::findByUserId)
                .flatMap(PanacheQuery::firstResult);

        asserter.assertThat(() -> joinAsTuple(reportSupplier.get(), targetSupplier.get()), tuple -> {
            Assertions.assertNull(tuple.getItem1().getTargetUser());
            Assertions.assertNull(tuple.getItem1().getTargetPost());
            Assertions.assertNotNull(tuple.getItem1().getTargetComment());
            Assertions.assertEquals(
                    tuple.getItem2().getId(),
                    tuple.getItem1().getTargetComment().getId());
        });
    }
}
