package app.fyreplace.services.mimetype;

import jakarta.annotation.Nonnull;

public enum KnownFileType {
    JPEG("image/jpeg"),
    PNG("image/png"),
    WEBP("image/webp");

    public final String mime;

    KnownFileType(@Nonnull String mime) {
        this.mime = mime;
    }
}
