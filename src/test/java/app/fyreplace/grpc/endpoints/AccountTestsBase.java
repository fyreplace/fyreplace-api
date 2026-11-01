package app.fyreplace.grpc.endpoints;

import app.fyreplace.grpc.GrpcTestsBase;
import app.fyreplace.protos.MutinyAccountServiceGrpc;
import io.quarkus.mailer.MockMailbox;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class AccountTestsBase extends GrpcTestsBase {
    private static final String ACTIVE_USER = "user_0";
    private static final String PENDING_USER = "pending_user";

    @Inject
    protected MockMailbox mailbox;

    private OffsetDateTime sweepThreshold;

    @BeforeEach
    void setup() {
        mailbox.clear();
        sweepThreshold = OffsetDateTime.now().minusMinutes(1);
        restoreScenarioUsers();
    }

    @AfterEach
    void teardown() {
        restoreScenarioUsers();
        awaitOnSafeContext(() -> userRepository.deleteByDateCreatedAfter(sweepThreshold));
    }

    protected final @Nonnull MutinyAccountServiceGrpc.MutinyAccountServiceStub accountService() {
        return MutinyAccountServiceGrpc.newMutinyStub(channel());
    }

    protected final @Nonnull MutinyAccountServiceGrpc.MutinyAccountServiceStub accountService(@Nullable String token) {
        return withToken(accountService(), token);
    }

    private void restoreScenarioUsers() {
        awaitOnSafeContext(() -> Uni.createFrom()
                .item(ACTIVE_USER)
                .call(userRepository::restoreByUsername)
                .replaceWith(PENDING_USER)
                .call(userRepository::restoreByUsername)
                .flatMap(userRepository::findByUsername)
                .call(pending -> userRepository.updateActiveById(pending.getId(), false)));
    }
}
