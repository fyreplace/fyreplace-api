package app.fyreplace.data;

import app.fyreplace.data.entities.User;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@RunOnVertxContext
public final class NotificationsTests extends DataTestsBase {
    @Test
    void testDeletedPostIsAbsentFromNotifications(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertEquals(notificationRepository::count, 1L);
        asserter.assertEquals(() -> userId("user_1").flatMap(notificationRepository::countByUserId), 1L);
        asserter.assertEquals(() -> userId("deleted_post_viewer").flatMap(notificationRepository::countByUserId), 0L);
    }

    private @Nonnull Uni<UUID> userId(@Nonnull String username) {
        return userRepository.findByUsername(username).map(User::getId);
    }
}
