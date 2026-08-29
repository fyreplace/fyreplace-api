package app.fyreplace.data.migration;

import app.fyreplace.data.entities.User;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import jakarta.annotation.Nonnull;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@RunOnVertxContext
public final class UsersMigrationTests extends MigrationTestsBase {
    @Test
    void testEntityCount(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertEquals(userRepository::count, 13L);
        asserter.assertEquals(blockRepository::count, 1L);
    }

    @Test
    void testAdmin(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertThat(() -> userRepository.findByUsername("the_admin"), user -> {
            Assertions.assertEquals("admin@example.org", user.getEmail());
            Assertions.assertEquals("!", user.getPassword());
            Assertions.assertTrue(user.isActive());
            Assertions.assertEquals(User.Rank.ADMIN, user.getRank());
            Assertions.assertEquals(OffsetDateTime.parse("2020-01-01T00:00:00Z"), user.getDateCreated());
            Assertions.assertEquals("I am admin", user.getBio());
            Assertions.assertNull(user.getAvatar());
            Assertions.assertEquals(User.BanCount.NEVER, user.getBanCount());
            Assertions.assertNull(user.getDateBanEnd());
            Assertions.assertFalse(user.isDeleted());
        });
    }

    @Test
    void testStaff(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertThat(() -> userRepository.findByUsername("the_staff"), user -> {
            Assertions.assertEquals("staff@example.org", user.getEmail());
            Assertions.assertEquals("!", user.getPassword());
            Assertions.assertTrue(user.isActive());
            Assertions.assertEquals(User.Rank.STAFF, user.getRank());
            Assertions.assertEquals(OffsetDateTime.parse("2020-01-08T00:00:00Z"), user.getDateCreated());
            Assertions.assertEquals("I am staff", user.getBio());
            Assertions.assertNull(user.getAvatar());
            Assertions.assertNull(user.getDateBanEnd());
            Assertions.assertFalse(user.isDeleted());
        });
    }

    @Test
    void testUser0(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertThat(() -> userRepository.findByUsername("user_0"), user -> {
            Assertions.assertEquals("user_0@example.org", user.getEmail());
            Assertions.assertEquals("!", user.getPassword());
            Assertions.assertTrue(user.isActive());
            Assertions.assertEquals(User.Rank.CITIZEN, user.getRank());
            Assertions.assertEquals(OffsetDateTime.parse("2021-03-12T00:00:00Z"), user.getDateCreated());
            Assertions.assertEquals("I am user 0", user.getBio());
            Assertions.assertNotNull(user.getAvatar());
            Assertions.assertEquals("avatars/user_0.png", user.getAvatar().getPath());
            Assertions.assertEquals(User.BanCount.NEVER, user.getBanCount());
            Assertions.assertNull(user.getDateBanEnd());
            Assertions.assertFalse(user.isDeleted());
        });
    }

    @Test
    void testUser1(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertThat(() -> userRepository.findByUsername("user_1"), user -> {
            Assertions.assertEquals("user_1@example.com", user.getEmail());
            Assertions.assertEquals("!", user.getPassword());
            Assertions.assertTrue(user.isActive());
            Assertions.assertEquals(User.Rank.CITIZEN, user.getRank());
            Assertions.assertEquals(OffsetDateTime.parse("2022-06-18T00:00:00Z"), user.getDateCreated());
            Assertions.assertEquals("I am user 1", user.getBio());
            Assertions.assertNotNull(user.getAvatar());
            Assertions.assertEquals("avatars/user_1.jpeg", user.getAvatar().getPath());
            Assertions.assertNull(user.getDateBanEnd());
            Assertions.assertFalse(user.isDeleted());
        });
    }

    @Test
    void testUser2(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertThat(() -> userRepository.findByUsername("user_2"), user -> {
            Assertions.assertEquals("user_2@example.com", user.getEmail());
            Assertions.assertEquals("!", user.getPassword());
            Assertions.assertTrue(user.isActive());
            Assertions.assertEquals(User.Rank.CITIZEN, user.getRank());
            Assertions.assertEquals(OffsetDateTime.parse("2021-09-01T00:00:00Z"), user.getDateCreated());
            Assertions.assertEquals("I am user 2", user.getBio());
            Assertions.assertNull(user.getAvatar());
            Assertions.assertNull(user.getDateBanEnd());
            Assertions.assertFalse(user.isDeleted());
        });
    }

    @Test
    void testBanned(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertThat(() -> userRepository.findByUsername("banned_user"), user -> {
            Assertions.assertEquals("banned_user@example.org", user.getEmail());
            Assertions.assertEquals("!", user.getPassword());
            Assertions.assertTrue(user.isActive());
            Assertions.assertEquals(User.Rank.CITIZEN, user.getRank());
            Assertions.assertEquals(OffsetDateTime.parse("2021-01-01T00:00:00Z"), user.getDateCreated());
            Assertions.assertEquals("I am banned", user.getBio());
            Assertions.assertNotNull(user.getAvatar());
            Assertions.assertEquals("avatars/banned.png", user.getAvatar().getPath());
            Assertions.assertEquals(User.BanCount.ONCE, user.getBanCount());
            Assertions.assertNotNull(user.getDateBanEnd());
            final var expectedBanEnd = OffsetDateTime.now().plus(Duration.ofDays(10));
            final var actualBanEnd = user.getDateBanEnd();
            final var distance = Duration.between(actualBanEnd, expectedBanEnd);
            Assertions.assertTrue(
                    distance.abs().truncatedTo(ChronoUnit.MINUTES).isZero(),
                    "Expected: " + expectedBanEnd + "; Actual: " + actualBanEnd);
            Assertions.assertFalse(user.isDeleted());
        });
    }

    @Test
    void testPermanentBanned(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertThat(() -> userRepository.findByUsername("permanent_banned_user"), user -> {
            Assertions.assertEquals("permanent_banned_user@example.org", user.getEmail());
            Assertions.assertTrue(user.isActive());
            Assertions.assertEquals(User.Rank.CITIZEN, user.getRank());
            Assertions.assertEquals(OffsetDateTime.parse("2021-02-01T00:00:00Z"), user.getDateCreated());
            Assertions.assertEquals("I am permanently banned", user.getBio());
            Assertions.assertNull(user.getAvatar());
            Assertions.assertEquals(User.BanCount.ONE_TOO_MANY, user.getBanCount());
            Assertions.assertNull(user.getDateBanEnd());
            Assertions.assertFalse(user.isDeleted());
        });
    }

    @Test
    void testUser1BlocksUser2(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertEquals(
                () -> userRepository
                        .findByUsername("user_1")
                        .map(User::getId)
                        .map(userRepository::findBlockedBy)
                        .flatMap(PanacheQuery::<User>firstResult)
                        .map(User::getUsername),
                "user_2");
    }

    @Test
    void testDeletedUsers(@Nonnull TransactionalUniAsserter asserter) {
        asserter.assertThat(
                () -> userRepository.find("isDeleted AND dateBanEnd IS NULL").singleResult(),
                user -> Assertions.assertEquals(User.BanCount.NEVER, user.getBanCount()));

        asserter.assertThat(
                () -> userRepository
                        .find("isDeleted AND dateBanEnd IS NOT NULL")
                        .singleResult(),
                user -> {
                    Assertions.assertEquals(User.BanCount.ONCE, user.getBanCount());
                    Assertions.assertFalse(user.isActive());
                    Assertions.assertNotNull(user.getDateBanEnd());
                });
    }
}
