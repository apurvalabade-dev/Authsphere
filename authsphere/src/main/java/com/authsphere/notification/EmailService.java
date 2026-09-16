package com.authsphere.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendVerificationEmail(String toEmail, String token) {
        String verifyUrl = baseUrl + "/api/v1/auth/verify-email?token=" + token;

        String body = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <h2 style="color: #333;">Verify Your Email</h2>
                <p>Thank you for registering with AuthSphere. Click the button below to verify your email address.</p>
                <a href="%s"
                   style="display: inline-block; padding: 12px 24px; background-color: #4CAF50;
                          color: white; text-decoration: none; border-radius: 4px; margin: 16px 0;">
                   Verify Email
                </a>
                <p style="color: #666; font-size: 14px;">This link expires in 24 hours.</p>
                <p style="color: #666; font-size: 14px;">If you did not register, ignore this email.</p>
            </div>
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
        String resetUrl = baseUrl + "/api/v1/auth/reset-password?token=" + token;

        String body = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <h2 style="color: #333;">Reset Your Password</h2>
                <p>We received a request to reset your AuthSphere password. Click the button below to proceed.</p>
                <a href="%s"
                   style="display: inline-block; padding: 12px 24px; background-color: #2196F3;
                          color: white; text-decoration: none; border-radius: 4px; margin: 16px 0;">
                   Reset Password
                </a>
                <p style="color: #666; font-size: 14px;">This link expires in 1 hour.</p>
                <p style="color: #666; font-size: 14px;">If you did not request a reset, ignore this email.</p>
            </div>
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
