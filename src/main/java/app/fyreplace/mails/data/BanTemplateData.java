package app.fyreplace.mails.data;

import jakarta.annotation.Nonnull;
import java.net.URI;

public record BanTemplateData(@Nonnull String date, @Nonnull URI logo) implements TemplateData {}
