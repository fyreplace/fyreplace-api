package app.fyreplace.rest;

import jakarta.annotation.Nonnull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
public final class HealthEndpoint {
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public @Nonnull String get() {
        return "";
    }
}
