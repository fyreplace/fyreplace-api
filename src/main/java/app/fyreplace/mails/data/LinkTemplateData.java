package app.fyreplace.mails.data;

import jakarta.annotation.Nonnull;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record LinkTemplateData(
        @Nonnull URI link,
        @Nonnull String customScheme,
        @Nonnull URI logo) implements TemplateData {

    public @Nonnull String linkB64() {
        return Base64.getEncoder()
                .encodeToString(
                        UriBuilder.fromUri(link).scheme(customScheme).toString().getBytes(StandardCharsets.UTF_8));
    }
}
