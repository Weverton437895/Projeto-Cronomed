package br.com.cronomed.notification;

import br.com.cronomed.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties props;

    public MailService(ObjectProvider<JavaMailSender> mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    public void sendPasswordResetCode(String to, String code) {
        String body = "Seu codigo CronoMed para redefinir a senha e: " + code
                + "\nEle expira em " + props.getSecurity().getOtpTtl().toMinutes() + " minutos."
                + "\nSe voce nao solicitou, ignore esta mensagem.";
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!props.getMail().isEnabled() || sender == null) {
            // Somente desenvolvimento: sem SMTP configurado, o codigo e registrado no log.
            log.warn("[DEV] E-mail desabilitado. Codigo de redefinicao para {}: {}", to, code);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.getMail().getFrom());
            msg.setTo(to);
            msg.setSubject("CronoMed - redefinicao de senha");
            msg.setText(body);
            sender.send(msg);
        } catch (Exception ex) {
            log.error("Falha ao enviar e-mail de redefinicao de senha", ex);
        }
    }
}
