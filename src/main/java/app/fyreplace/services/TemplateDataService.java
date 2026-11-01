package app.fyreplace.services;

import app.fyreplace.data.entities.User;
import app.fyreplace.mails.data.BanTemplateData;
import app.fyreplace.mails.data.LinkTemplateData;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public final class TemplateDataService {
    @ConfigProperty(name = "app.url")
    URI appUrl;

    @ConfigProperty(name = "app.front.url")
    URI appFrontUrl;

    @ConfigProperty(name = "app.front.custom-scheme")
    String appFrontCustomScheme;

    @Inject
    JwtService jwtService;

    public @Nonnull BanTemplateData getData(@Nonnull String dateEnd) {
        return new BanTemplateData(dateEnd, logo());
    }

    public @Nonnull LinkTemplateData getData(@Nonnull String actionEndpoint, @Nonnull User user) {
        return new LinkTemplateData(link(actionEndpoint, user), appFrontCustomScheme, logo());
    }

    public @Nonnull LinkTemplateData getData(
            @Nonnull String actionEndpoint, @Nonnull User user, @Nonnull String newEmail) {
        return new LinkTemplateData(link(actionEndpoint, user, newEmail), appFrontCustomScheme, logo());
    }

    public @Nonnull URI link(@Nonnull String path, @Nonnull User user) {
        return UriBuilder.fromUri(appFrontUrl)
                .path(path)
                .fragment(jwtService.makeJwt(user, true))
                .build();
    }

    public @Nonnull URI link(@Nonnull String path, @Nonnull User user, @Nonnull String newEmail) {
        return UriBuilder.fromUri(appFrontUrl)
                .path(path)
                .fragment(jwtService.makeEmailUpdateJwt(user, newEmail))
                .build();
    }

    public @Nonnull URI logo() {
        return UriBuilder.fromUri(appUrl).path("/images/logo.png").build();
    }
}
