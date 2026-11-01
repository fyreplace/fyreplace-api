package app.fyreplace.mails.data;

import jakarta.annotation.Nonnull;
import java.net.URI;

public record LinkTemplateData(
        @Nonnull URI link, @Nonnull String qrCode, @Nonnull URI logo) implements TemplateData {}
