package app.fyreplace.services;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.RequestScoped;
import java.util.Locale;
import java.util.ResourceBundle;

@RequestScoped
public final class LocaleService {
    public @Nonnull Locale getLocale() {
        return Locale.getDefault();
    }

    public @Nonnull ResourceBundle getResourceBundle(@Nonnull String name) {
        return ResourceBundle.getBundle("i18n." + name, getLocale());
    }
}
