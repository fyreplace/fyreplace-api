package app.fyreplace.mails;

import app.fyreplace.data.entities.User;
import app.fyreplace.mails.data.LinkTemplateData;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ResourceBundle;

@ApplicationScoped
public final class AccountConnectionMail extends MailBase {
    @Override
    protected @Nonnull String getSubjectKey() {
        return "account_connection_subject";
    }

    @Override
    protected @Nonnull TemplateInstance getTextTemplate(@Nonnull User user) {
        return Templates.text(
                getResourceBundle(), templateCommonData.getData("AccountService.ConfirmConnection", user));
    }

    @Override
    protected @Nonnull TemplateInstance getHtmlTemplate(@Nonnull User user) {
        return Templates.html(
                getResourceBundle(), templateCommonData.getData("AccountService.ConfirmConnection", user));
    }

    @CheckedTemplate
    public static class Templates {
        public static native @Nonnull TemplateInstance text(@Nonnull ResourceBundle r, @Nonnull LinkTemplateData d);

        public static native @Nonnull TemplateInstance html(@Nonnull ResourceBundle r, @Nonnull LinkTemplateData d);
    }
}
