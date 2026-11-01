package app.fyreplace.services.gravatar;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@Path("/avatar")
public interface GravatarApi {
    @GET
    @Path("/{hash}")
    @Nonnull
    Uni<byte[]> getAvatar(
            @Nonnull @PathParam("hash") String hash,
            @Nonnull @QueryParam("d") String defaultImage,
            @QueryParam("s") int size);
}
