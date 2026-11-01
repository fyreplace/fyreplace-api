package app.fyreplace.mails;

import app.fyreplace.data.entities.User;
import app.fyreplace.mails.data.TemplateData;
import app.fyreplace.services.LocaleService;
import app.fyreplace.services.TemplateDataService;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

public abstract class MailBase<D extends TemplateData> {
    @Inject
    ReactiveMailer mailer;

    @Inject
    LocaleService localeService;

    @Inject
    TemplateDataService templateCommonData;

    protected abstract @Nonnull String getSubjectKey();

    protected abstract @Nonnull TemplateInstance getTextTemplate(@Nonnull D data);

    protected abstract @Nonnull TemplateInstance getHtmlTemplate(@Nonnull D data);

    public @Nonnull ResourceBundle getResourceBundle() {
        return localeService.getResourceBundle("strings_emails");
    }

    public @Nonnull Uni<Void> send(@Nonnull User recipient, @Nonnull D data) {
        return send(List.of(Objects.requireNonNull(recipient.getEmail())), data);
    }

    public @Nonnull Uni<Void> send(@Nonnull List<String> recipients, @Nonnull D data) {
        return send(recipients, getTextTemplate(data), getHtmlTemplate(data));
    }

    protected @Nonnull Uni<Void> send(
            @Nonnull List<String> recipients,
            @Nonnull TemplateInstance textTemplate,
            @Nonnull TemplateInstance htmlTemplate) {
        return Uni.createFrom()
                .item(new Mail())
                .invoke(mail -> mail.setSubject(getResourceBundle().getString(getSubjectKey())))
                .invoke(mail -> mail.setTo(recipients))
                .call(mail -> render(textTemplate).invoke(mail::setText))
                .call(mail -> render(htmlTemplate).invoke(mail::setHtml))
                .flatMap(mailer::send);
    }

    private static @Nonnull Uni<String> render(@Nonnull TemplateInstance template) {
        return Uni.createFrom()
                .item(template)
                .map(TemplateInstance::renderAsync)
                .flatMap(Uni.createFrom()::completionStage);
    }
}
