package com.certichain.wallet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class MailGateway {

    private static final Logger log = LoggerFactory.getLogger(MailGateway.class);

    private final ObjectProvider<JavaMailSender> mail;
    private final String host;

    public MailGateway(ObjectProvider<JavaMailSender> mail, @Value("${spring.mail.host:}") String host) {
        this.mail = mail;
        this.host = host == null ? "" : host.trim();
    }

    public boolean configured() {
        return !host.isBlank() && mail.getIfAvailable() != null;
    }

    public boolean send(String to, String subject, String body) {
        if (to == null || !to.contains("@") || !configured()) {
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to.trim());
            message.setSubject(subject);
            message.setText(body);
            mail.getIfAvailable().send(message);
            return true;
        } catch (RuntimeException exception) {
            log.warn("Email was not sent. {}", exception.getMessage());
            return false;
        }
    }
}
