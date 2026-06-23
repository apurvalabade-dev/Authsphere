package com.authsphere.notification;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        String verifyUrl = "http://localhost:8080/api/v1/auth/verify-email?token=" + token;
        String body = """
            <h2>Verify Your Email</h2>
            <p>Click the link below to verify your AuthSphere account:</p>
            <a href="%s">Verify Email</a>
            <p>This link expires in 24 hours.</p>
            """.formatted(verifyUrl);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(toEmail);
            helper.setFrom("noreply@authsphere.com");
            helper.setSubject("Verify your AuthSphere account");
            helper.setText(body, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send verification email: " + e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String token) {
        String resetUrl = "http://localhost:8080/api/v1/auth/reset-password?token=" + token;
        String body = """
            <h2>Reset Your Password</h2>
            <p>Click the link below to reset your AuthSphere password:</p>
            <a href="%s">Reset Password</a>
            <p>This link expires in 1 hour.</p>
            """.formatted(resetUrl);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(toEmail);
            helper.setFrom("noreply@authsphere.com");
            helper.setSubject("Reset your AuthSphere password");
            helper.setText(body, true);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send password reset email: " + e.getMessage());
        }
    }
}
