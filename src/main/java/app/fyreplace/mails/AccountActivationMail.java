package app.fyreplace.mails;

import app.fyreplace.data.entities.User;
import app.fyreplace.mails.data.LinkTemplateData;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ResourceBundle;

@ApplicationScoped
public final class AccountActivationMail extends MailBase<LinkTemplateData> {
    @Override
    protected @Nonnull String getSubjectKey() {
        return "account_activation_subject";
    }

    public @Nonnull Uni<Void> send(@Nonnull User user) {
        return send(user, templateCommonData.getData("AccountService.ConfirmActivation", user));
    }

    @Override
    protected @Nonnull TemplateInstance getTextTemplate(@Nonnull LinkTemplateData data) {
        return Templates.text(getResourceBundle(), data);
    }

    @Override
    protected @Nonnull TemplateInstance getHtmlTemplate(@Nonnull LinkTemplateData data) {
        return Templates.html(getResourceBundle(), data);
    }

    @CheckedTemplate
    public static class Templates {
        public static native @Nonnull TemplateInstance text(@Nonnull ResourceBundle r, @Nonnull LinkTemplateData d);

        public static native @Nonnull TemplateInstance html(@Nonnull ResourceBundle r, @Nonnull LinkTemplateData d);
    }
}
