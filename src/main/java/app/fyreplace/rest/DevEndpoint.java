package app.fyreplace.rest;

import app.fyreplace.data.entities.User;
import app.fyreplace.services.JwtService;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.flywaydb.core.Flyway;

@Path("dev")
@IfBuildProperty(name = "app.local-dev", stringValue = "true")
public final class DevEndpoint {
    @Inject
    User.Repository userRepository;

    @Inject
    JwtService jwtService;

    @Inject
    Flyway flyway;

    @POST
    @Path("database/populate")
    public @Nonnull Uni<Void> populateDatabase() {
        return Uni.createFrom()
                .voidItem()
                .invoke(flyway::clean)
                .invoke(flyway::baseline)
                .invoke(flyway::migrate)
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    @GET
    @Path("users/{username}/token")
    public @Nonnull Uni<String> getUserToken(@Nonnull @PathParam("username") String username) {
        return userRepository.findByUsername(username).map(user -> jwtService.makeJwt(user, false));
    }

    @GET
    @Path("passwords/{password}/hash")
    public @Nonnull Uni<String> getPasswordHash(@Nonnull @PathParam("password") String password) {
        return Uni.createFrom()
                .item(password)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .map(BcryptUtil::bcryptHash)
                .emitOn(Infrastructure.getDefaultExecutor());
    }
}
