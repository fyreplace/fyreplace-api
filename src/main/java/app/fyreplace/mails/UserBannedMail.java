package app.fyreplace.mails;

import app.fyreplace.data.entities.User;
import app.fyreplace.mails.data.BanTemplateData;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ResourceBundle;

@ApplicationScoped
public final class UserBannedMail extends MailBase<BanTemplateData> {
    @Override
    protected @Nonnull String getSubjectKey() {
        return "user_banned_subject";
    }

    public @Nonnull Uni<Void> send(@Nonnull User user) {
        return send(user, templateCommonData.getData(getFormattedDate(user)));
    }

    @Override
    protected @Nonnull TemplateInstance getTextTemplate(@Nonnull BanTemplateData data) {
        return Templates.text(getResourceBundle(), data);
    }

    @Override
    protected @Nonnull TemplateInstance getHtmlTemplate(@Nonnull BanTemplateData data) {
        return Templates.html(getResourceBundle(), data);
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
