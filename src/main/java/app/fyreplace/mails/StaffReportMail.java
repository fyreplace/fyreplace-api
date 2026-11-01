package app.fyreplace.mails;

import app.fyreplace.data.entities.User;
import app.fyreplace.mails.data.ReportTemplateData;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.ResourceBundle;

@ApplicationScoped
public final class StaffReportMail extends MailBase<ReportTemplateData> {
    @Inject
    User.Repository userRepository;

    @Override
    protected @Nonnull String getSubjectKey() {
        return "report_subject";
    }

    public @Nonnull Uni<Void> sendToStaff(@Nonnull URI link) {
        return userRepository
                .findStaffEmails()
                .flatMap(emails -> emails.isEmpty()
                        ? Uni.createFrom().voidItem()
                        : send(emails, templateCommonData.getReportData(link)));
    }

    @Override
    protected @Nonnull TemplateInstance getTextTemplate(@Nonnull ReportTemplateData data) {
        return Templates.text(getResourceBundle(), data);
    }

    @Override
    protected @Nonnull TemplateInstance getHtmlTemplate(@Nonnull ReportTemplateData data) {
        return Templates.html(getResourceBundle(), data);
    }

    @CheckedTemplate
    public static class Templates {
        public static native @Nonnull TemplateInstance text(@Nonnull ResourceBundle r, @Nonnull ReportTemplateData d);

        public static native @Nonnull TemplateInstance html(@Nonnull ResourceBundle r, @Nonnull ReportTemplateData d);
    }
}
