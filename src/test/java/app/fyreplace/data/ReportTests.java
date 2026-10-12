package app.fyreplace.data;

import app.fyreplace.data.entities.Report;
import app.fyreplace.data.entities.User;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public final class ReportTests extends DataTestsBase {
    private @Nullable UUID createdReportId;

    @BeforeEach
    void setup() {
        awaitOnSafeContext(this::createReport);
    }

    @AfterEach
    void teardown() {
        if (createdReportId != null) {
            awaitOnSafeContext(() -> reportRepository.deleteById(createdReportId));
        }
    }

    @Test
    @RunOnVertxContext
    void testReportWithOnlyOneTargetPersists(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertThat(() -> reportRepository.find("id", createdReportId).singleResult(), report -> {
            Assertions.assertEquals(
                    "user_1", report.getIssuer().getUsername(), "the persisted row must reference the issuer");
            Assertions.assertEquals(
                    "user_2", report.getTargetUser().getUsername(), "the persisted row must reference the target user");
            Assertions.assertNull(report.getTargetPost(), "untouched targets must remain null");
            Assertions.assertNull(report.getTargetComment());
        });
    }

    @Test
    void testDuplicateReportIsRejected() {
        try {
            awaitOnSafeContext(this::createReport);
            Assertions.fail("a second identical report was accepted");
        } catch (IllegalStateException expected) {
            Assertions.assertTrue(isUniqueViolation(expected), "expected a unique violation, got: " + expected);
        }
    }

    private @Nonnull Report reportAgainst(@Nonnull User issuer, @Nonnull User target) {
        final var report = new Report();
        report.setIssuer(issuer);
        report.setTargetUser(target);
        return report;
    }

    private @Nonnull Uni<Void> createReport() {
        return joinAsTuple(userRepository.findByUsername("user_1"), userRepository.findByUsername("user_2"))
                .map(t -> reportAgainst(t.getItem1(), t.getItem2()))
                .flatMap(reportRepository::persist)
                .flatMap(DataTestsBase::refresh)
                .map(Report::getId)
                .invoke(id -> createdReportId = id)
                .replaceWithVoid();
    }
}
