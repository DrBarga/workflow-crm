package dev.workflowcrm.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(OutputCaptureExtension.class)
class SmtpMailDeliveryTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);

    @Test
    void reportsMissingVariablesWithoutTheirValues(CapturedOutput output) {
        var delivery = new SmtpMailDelivery(provider, "", "", "", true);
        assertThat(delivery.available("", "AUTH_EMAIL_FROM")).isFalse();
        assertThat(output.getAll()).contains("SMTP_HOST", "SMTP_USERNAME", "SMTP_PASSWORD", "AUTH_EMAIL_FROM");
    }

    @Test
    void authenticatedSmtpRequiresBothCredentials(CapturedOutput output) {
        var delivery = new SmtpMailDelivery(provider, "smtp.internal", "synthetic-user", "", true);
        assertThat(delivery.available("sender@example.com", "AUTH_EMAIL_FROM")).isFalse();
        assertThat(output.getAll()).contains("SMTP_PASSWORD").doesNotContain("synthetic-user", "smtp.internal");
    }

    @Test
    void localSmtpDoesNotRequireAuthentication() {
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);
        var delivery = new SmtpMailDelivery(provider, "mailpit", "", "", false);
        var message = new SimpleMailMessage();
        message.setFrom("sender@localhost");
        delivery.send(message, "AUTH_EMAIL_FROM");
        verify(sender).send(message);
    }

    @Test
    void providerErrorsNeverExposeCredentialsOrEmailContents(CapturedOutput output) {
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);
        var message = new SimpleMailMessage();
        message.setFrom("sender@example.com");
        doThrow(new MailAuthenticationException("synthetic-password secret-code recipient@example.com"))
                .when(sender)
                .send(message);
        var delivery = new SmtpMailDelivery(provider, "smtp.internal", "user", "synthetic-password", true);
        assertThatThrownBy(() -> delivery.send(message, "AUTH_EMAIL_FROM"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SMTP delivery failed")
                .hasNoCause();
        assertThat(output.getAll())
                .contains("authentication failed")
                .doesNotContain("synthetic-password", "secret-code", "recipient@example.com");
    }
}
