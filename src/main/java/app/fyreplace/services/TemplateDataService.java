package app.fyreplace.services;

import app.fyreplace.data.entities.Comment;
import app.fyreplace.data.entities.User;
import app.fyreplace.mails.data.BanTemplateData;
import app.fyreplace.mails.data.LinkTemplateData;
import app.fyreplace.mails.data.ReportTemplateData;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;
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
    Comment.Repository commentRepository;

    @Inject
    JwtService jwtService;

    @Inject
    QrCodeService qrCodeService;

    public @Nonnull BanTemplateData getData(@Nonnull String dateEnd) {
        return new BanTemplateData(dateEnd, logo());
    }

    public @Nonnull LinkTemplateData getData(@Nonnull String actionEndpoint, @Nonnull User user) {
        return getData(link(actionEndpoint, user));
    }

    public @Nonnull LinkTemplateData getData(
            @Nonnull String actionEndpoint, @Nonnull User user, @Nonnull String newEmail) {
        return getData(link(actionEndpoint, user, newEmail));
    }

    public @Nonnull LinkTemplateData getData(@Nonnull URI link) {
        return new LinkTemplateData(link, qrCode(appLink(link)), logo());
    }

    public @Nonnull ReportTemplateData getReportData(@Nonnull URI link) {
        return new ReportTemplateData(link, qrCode(appLink(link)), logo());
    }

    public @Nonnull URI userFrontLink(@Nonnull UUID userId) {
        return frontLink("u/" + b64UrlId(userId));
    }

    public @Nonnull URI postFrontLink(@Nonnull UUID postId) {
        return frontLink("p/" + b64UrlId(postId));
    }

    public @Nonnull Uni<URI> commentFrontLink(@Nonnull Comment comment) {
        return commentRepository
                .countBeforeByPostId(comment.getPost().getId(), comment)
                .map(position -> frontLink("p/" + b64UrlId(comment.getPost().getId()) + "/" + position));
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

    public static @Nonnull String b64UrlId(@Nonnull UUID id) {
        final var buffer = ByteBuffer.allocate(16);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    private @Nonnull URI frontLink(@Nonnull String path) {
        return UriBuilder.fromUri(appFrontUrl).path(path).build();
    }

    private @Nonnull String appLink(@Nonnull URI link) {
        return UriBuilder.fromUri(link).scheme(appFrontCustomScheme).build().toString();
    }

    private @Nonnull String qrCode(@Nonnull String content) {
        return qrCodeService.encodeToBase64Png(content);
    }
}
