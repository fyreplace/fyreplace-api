package app.fyreplace.mails;

import app.fyreplace.data.entities.User;
import app.fyreplace.mails.data.BanTemplateData;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ResourceBundle;

@ApplicationScoped
public final class UserBannedMail extends MailBase {
    @Override
    protected @Nonnull String getSubjectKey() {
        return "user_banned_subject";
    }

    @Override
    protected @Nonnull TemplateInstance getTextTemplate(@Nonnull User user) {
        return Templates.text(getResourceBundle(), templateCommonData.getData(getFormattedDate(user)));
    }

    @Override
    protected @Nonnull TemplateInstance getHtmlTemplate(@Nonnull User user) {
        return Templates.html(getResourceBundle(), templateCommonData.getData(getFormattedDate(user)));
    }

    private @Nonnull String getFormattedDate(@Nonnull User user) {
        final var date = user.getDateBanEnd();
        final var formatter =
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withLocale(localeService.getLocale());
        return date != null ? formatter.format(date) : getResourceBundle().getString("user_banned_forever");
    }

    @CheckedTemplate
    public static class Templates {
        public static native @Nonnull TemplateInstance text(@Nonnull ResourceBundle r, @Nonnull BanTemplateData d);

        public static native @Nonnull TemplateInstance html(@Nonnull ResourceBundle r, @Nonnull BanTemplateData d);
    }
}
