package app.fyreplace.services;

import app.fyreplace.data.entities.User;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public final class BanService {
    @Inject
    ExecutorService executorService;

    @Inject
    User.Repository userRepository;

    @Scheduled(every = "1h")
    public void liftExpiredBans() {
        executorService.awaitOnSafeContext(userRepository::unbanExpired);
    }
}
