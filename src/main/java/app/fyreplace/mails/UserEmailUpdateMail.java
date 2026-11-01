package app.fyreplace.mails;

import app.fyreplace.data.entities.User;
import app.fyreplace.mails.data.LinkTemplateData;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.ResourceBundle;

@ApplicationScoped
public final class UserEmailUpdateMail extends MailBase {
    @Override
    protected @Nonnull String getSubjectKey() {
        return "user_email_update_subject";
    }

    @Override
    protected @Nonnull TemplateInstance getTextTemplate(@Nonnull User user) {
        return Templates.text(getResourceBundle(), templateCommonData.getData("UserService.ConfirmEmailUpdate", user));
    }

    @Override
    protected @Nonnull TemplateInstance getHtmlTemplate(@Nonnull User user) {
        return Templates.html(getResourceBundle(), templateCommonData.getData("UserService.ConfirmEmailUpdate", user));
    }

    public @Nonnull Uni<Void> send(@Nonnull User recipient, @Nonnull String newEmail) {
        final var data = templateCommonData.getData("UserService.ConfirmEmailUpdate", recipient, newEmail);
        return send(
                List.of(newEmail),
                Templates.text(getResourceBundle(), data),
                Templates.html(getResourceBundle(), data));
    }

    @CheckedTemplate
    public static class Templates {
        public static native @Nonnull TemplateInstance text(@Nonnull ResourceBundle r, @Nonnull LinkTemplateData d);

        public static native @Nonnull TemplateInstance html(@Nonnull ResourceBundle r, @Nonnull LinkTemplateData d);
    }
}
