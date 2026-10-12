package app.fyreplace.data;

import app.fyreplace.data.entities.User;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
public final class UsersBanTests extends DataTestsBase {
    private static final String TARGET_USERNAME = "ban_target";

    @BeforeEach
    @AfterEach
    void setupAndTeardown() {
        awaitOnSafeContext(() -> userRepository.resetBanByUsername(TARGET_USERNAME));
    }

    @Test
    @RunOnVertxContext
    void testFirstBanIsTimed(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() ->
                finish(userRepository.findByUsername(TARGET_USERNAME).flatMap(user -> ban(user, 5)), user -> {
                    Assertions.assertEquals(User.BanCount.ONCE, user.getBanCount());
                    Assertions.assertNotNull(user.getDateBanEnd(), "the first ban must be timed");
                }));
    }

    @Test
    @RunOnVertxContext
    void testSecondBanBecomesPermanent(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() -> finish(
                userRepository
                        .findByUsername(TARGET_USERNAME)
                        .flatMap(user -> ban(user, 5))
                        .flatMap(user -> ban(user, 5)),
                user -> {
                    Assertions.assertEquals(User.BanCount.ONE_TOO_MANY, user.getBanCount());
                    Assertions.assertNull(user.getDateBanEnd(), "re-banning must clear the timed ban");
                }));
    }

    @Test
    @RunOnVertxContext
    void testBanCountIsCapped(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() -> finish(
                userRepository
                        .findByUsername(TARGET_USERNAME)
                        .flatMap(user -> ban(user, 1))
                        .flatMap(user -> ban(user, 1))
                        .flatMap(user -> ban(user, 1))
                        .flatMap(user -> ban(user, 1)),
                user -> Assertions.assertEquals(
                        User.BanCount.ONE_TOO_MANY,
                        user.getBanCount(),
                        "ban_count must never leave the range of the mapped BanCount enum")));
    }

    private @Nonnull Uni<User> ban(@Nonnull User user, int days) {
        return userRepository.updateBanByUserId(user.getId(), days).replaceWith(user);
    }

    private @Nonnull Uni<Void> finish(@Nonnull Uni<User> banned, @Nonnull Consumer<User> assertions) {
        return banned.flatMap(UsersBanTests::refresh).invoke(assertions).replaceWithVoid();
    }
}
