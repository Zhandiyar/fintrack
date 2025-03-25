package kz.finance.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender emailSender;

    public void sendSimpleMessage(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            emailSender.send(message);
            log.info("Письмо успешно отправлено на {}", to);
        } catch (Exception e) {
            log.error("Ошибка при отправке письма", e); // <-- ВАЖНО!
            throw new RuntimeException("Ошибка при отправке письма", e); // или твоя кастомная
        }
    }
}