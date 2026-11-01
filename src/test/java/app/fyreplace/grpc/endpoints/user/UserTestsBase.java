package app.fyreplace.grpc.endpoints.user;

import app.fyreplace.data.entities.Block;
import app.fyreplace.data.entities.User;
import app.fyreplace.grpc.GrpcTestsBase;
import app.fyreplace.protos.MutinyUserServiceGrpc;
import app.fyreplace.services.ToProtoService;
import io.quarkus.mailer.MockMailbox;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class UserTestsBase extends GrpcTestsBase {
    protected static final String ACTIVE_USER = "user_0";
    protected static final String OTHER_USER = "user_1";
    protected static final String TARGET_USER = "user_2";
    protected static final String STAFF_USER = "the_staff";
    protected static final String ADMIN_USER = "the_admin";
    protected static final String BANNED_USER = "banned_user";
    protected static final String PERM_BANNED_USER = "permanent_banned_user";
    protected static final String BAN_TARGET = "ban_target";

    @Inject
    protected Block.Repository blockRepository;

    @Inject
    protected ToProtoService toProtoService;

    @Inject
    protected MockMailbox mailbox;

    private OffsetDateTime sweepThreshold;

    @BeforeEach
    void setup() {
        mailbox.clear();
        sweepThreshold = OffsetDateTime.now().minusMinutes(1);
        restoreSeededUsers();
    }

    @AfterEach
    void teardown() {
        restoreSeededUsers();
        awaitOnSafeContext(() -> userRepository.deleteByDateCreatedAfter(sweepThreshold));
    }

    protected final @Nonnull MutinyUserServiceGrpc.MutinyUserServiceStub userService() {
        return MutinyUserServiceGrpc.newMutinyStub(channel());
    }

    protected final @Nonnull MutinyUserServiceGrpc.MutinyUserServiceStub userService(@Nullable String token) {
        return withToken(userService(), token);
    }

    protected final @Nonnull String emailToken(@Nonnull User user, @Nonnull String newEmail) {
        return jwtService.makeEmailUpdateJwt(user, newEmail);
    }

    private void restoreSeededUsers() {
        awaitOnSafeContext(() -> Uni.createFrom()
                .voidItem()
                .chain(() -> userRepository.resetBanByUsername(BAN_TARGET))
                .chain(() -> userRepository.resetBanByUsername(OTHER_USER))
                .chain(() -> userRepository.resetActiveByUsername(OTHER_USER))
                .chain(() -> userRepository.updateRankByUsername(BAN_TARGET, User.Rank.CITIZEN))
                .chain(() -> userRepository.updateRankByUsername(ACTIVE_USER, User.Rank.CITIZEN))
                .chain(() -> userRepository.updateRankByUsername(OTHER_USER, User.Rank.CITIZEN))
                .chain(() -> userRepository.updateRankByUsername(STAFF_USER, User.Rank.STAFF))
                .chain(() -> userRepository.updateRankByUsername(ADMIN_USER, User.Rank.ADMIN))
                .chain(() -> userRepository.resetBioByUsername(ACTIVE_USER, "I am user 0"))
                .chain(() -> userRepository.resetEmailByUsername(ACTIVE_USER, "user_0@example.org"))
                .chain(() -> userRepository.findByUsername(ACTIVE_USER))
                .call(active -> userRepository.storeOrRemoveAvatarById(active.getId(), new byte[0]))
                .chain(() -> blockRepository.deleteAll())
                .replaceWithVoid());
    }
}
