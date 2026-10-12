package app.fyreplace.data;

import app.fyreplace.data.entities.Post;
import app.fyreplace.data.entities.User;
import app.fyreplace.data.entities.Vote;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
@RunOnVertxContext
public final class VotesCastTests extends DataTestsBase {
    private static final String POST_AUTHOR = "chapter_author";
    private static final String VOTER = "user_1";

    @Test
    void testFirstSpreadVoteGrantsLife(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() -> loadFixture(VOTER).flatMap(fixture -> {
            final var lifeBefore = fixture.post().getLife();
            final var vote = transientVote(fixture.voter(), fixture.post(), true);
            return voteRepository
                    .castOrNull(vote)
                    .invoke(Assertions::assertNotNull)
                    .flatMap(DataTestsBase::refresh)
                    .call(() -> refresh(fixture.post()))
                    .invoke(first -> {
                        Assertions.assertEquals(
                                fixture.voter().getId(), first.getUser().getId());
                        Assertions.assertEquals(
                                fixture.post().getId(), first.getPost().getId());
                        Assertions.assertTrue(first.isSpread());
                        Assertions.assertEquals(
                                lifeBefore + Vote.SPREAD_LIFE, fixture.post().getLife());
                    })
                    .eventually(() -> {
                        fixture.post().setLife(lifeBefore);
                        return deleteVote(fixture);
                    });
        }));
    }

    @Test
    void testVoteRenewalReturnsExistingRow(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() -> loadFixture(VOTER).flatMap(fixture -> {
            final var lifeBefore = fixture.post().getLife();
            final var vote = transientVote(fixture.voter(), fixture.post(), true);
            return voteRepository
                    .castOrNull(vote)
                    .invoke(Assertions::assertNotNull)
                    .flatMap(first -> voteRepository
                            .castOrNull(vote)
                            .invoke(Assertions::assertNotNull)
                            .flatMap(DataTestsBase::refresh)
                            .invoke(renewal -> Assertions.assertEquals(first.getId(), renewal.getId()))
                            .call(() -> refresh(fixture.post()))
                            .invoke(() -> Assertions.assertEquals(
                                    lifeBefore + Vote.SPREAD_LIFE,
                                    fixture.post().getLife())))
                    .eventually(() -> {
                        fixture.post().setLife(lifeBefore);
                        return deleteVote(fixture);
                    });
        }));
    }

    @Test
    void testFirstPlainVoteGrantsNoLife(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() -> loadFixture(VOTER).flatMap(fixture -> {
            final var lifeBefore = fixture.post().getLife();
            final var vote = transientVote(fixture.voter(), fixture.post(), false);
            return voteRepository
                    .castOrNull(vote)
                    .invoke(Assertions::assertNotNull)
                    .flatMap(DataTestsBase::refresh)
                    .call(() -> refresh(fixture.post()))
                    .map(Vote::isSpread)
                    .invoke(Assertions::assertFalse)
                    .invoke(() -> Assertions.assertEquals(lifeBefore, fixture.post().getLife()))
                    .eventually(() -> deleteVote(fixture));
        }));
    }

    @Test
    void testSelfVoteIsNoOp(@Nonnull TransactionalUniAsserter asserter) {
        asserter.execute(() -> loadFixture(POST_AUTHOR).flatMap(fixture -> {
            final var lifeBefore = fixture.post().getLife();
            return voteRepository
                    .castOrNull(transientVote(fixture.voter(), fixture.post(), true))
                    .invoke(Assertions::assertNull)
                    .call(() -> voteRepository.findByUserIdAndPostId(
                            fixture.voter().getId(), fixture.post().getId()))
                    .invoke(Assertions::assertNull)
                    .call(() -> refresh(fixture.post()))
                    .invoke(() ->
                            Assertions.assertEquals(lifeBefore, fixture.post().getLife()));
        }));
    }

    private @Nonnull Uni<Fixture> loadFixture(@Nonnull String voter) {
        return joinAsTuple(userRepository.findByUsername(voter), loadPost())
                .map(t -> new Fixture(t.getItem1(), t.getItem2()));
    }

    private @Nonnull Uni<Post> loadPost() {
        return userRepository
                .findByUsername(POST_AUTHOR)
                .map(User::getId)
                .map(postRepository::findByAuthorId)
                .flatMap(PanacheQuery::list)
                .map(List::getFirst);
    }

    private @Nonnull Uni<Void> deleteVote(@Nonnull Fixture fixture) {
        return voteRepository
                .findByUserIdAndPostId(fixture.voter().getId(), fixture.post().getId())
                .flatMap(vote -> vote == null ? Uni.createFrom().nullItem() : voteRepository.delete(vote))
                .replaceWithVoid();
    }

    private static @Nonnull Vote transientVote(@Nonnull User user, @Nonnull Post post, boolean spread) {
        final var vote = new Vote();
        vote.setUser(user);
        vote.setPost(post);
        vote.setSpread(spread);
        return vote;
    }

    private record Fixture(@Nonnull User voter, @Nonnull Post post) {}
}
