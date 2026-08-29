package app.fyreplace.data.migration;

import app.fyreplace.data.entities.MessagingToken;
import app.fyreplace.data.entities.User;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@RunOnVertxContext
public final class MessagingTokensMigrationTests extends MigrationTestsBase {
    @Test
    void testEntityCount(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertEquals(messagingTokenRepository::count, 2L);
    }

    @Test
    void testUser0(@Nonnull TransactionalUniAsserter asserter) {
        final Supplier<Uni<MessagingToken>> tokenSupplier = () -> userRepository
                .findByUsername("user_0")
                .map(User::getId)
                .map(messagingTokenRepository::findByUserId)
                .flatMap(PanacheQuery::singleResult);

        asserter.assertThat(tokenSupplier, token -> {
            Assertions.assertEquals(MessagingToken.Service.APNS, token.getService());
            Assertions.assertEquals("apns-token-user-0", token.getToken());
        });
    }

    @Test
    void testUser1(@Nonnull TransactionalUniAsserter asserter) {
        final Supplier<Uni<MessagingToken>> tokenSupplier = () -> userRepository
                .findByUsername("user_1")
                .map(User::getId)
                .map(messagingTokenRepository::findByUserId)
                .flatMap(PanacheQuery::singleResult);

        asserter.assertThat(tokenSupplier, token -> {
            Assertions.assertEquals(MessagingToken.Service.FCM, token.getService());
            Assertions.assertEquals("fcm-token-user-1", token.getToken());
        });
    }
}
