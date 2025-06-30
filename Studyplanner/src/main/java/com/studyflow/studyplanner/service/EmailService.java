package com.studyflow.studyplanner.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * Service for sending emails, e.g. for password reset functionality.
 */
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Sends a password reset email with a reset token link to the specified address.
     */
    public void sendPasswordResetEmail(String to, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom("noreply.studyflow@gmail.com");
            helper.setTo(to);
            helper.setSubject("Password Reset Request");

            String resetUrl = "https://studyflow.up.railway.app/reset-password?token=" + token;

            String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;background:#f9f9f9;padding:32px 24px;border-radius:12px;">
                  <h2 style="color:#2474a6;margin-top:0;">🔒 Reset your password</h2>
                  <p style="font-size:1.1rem;color:#222;">
                    You requested to reset your StudyFlow password.<br>
                    Click the button below to set a new password:
                  </p>
                  <div style="text-align:center;margin:32px 0;">
                    <a href="%s" style="background:#33bbee;color:#fff;text-decoration:none;padding:14px 32px;border-radius:7px;font-size:1.1rem;font-weight:bold;display:inline-block;">
                      Reset Password
                    </a>
                  </div>
                  <p style="color:#666;font-size:0.98rem;">
                    If you did not request this, you can ignore this email.<br>
                    The link is only valid for a limited time.
                  </p>
                  <hr style="margin:24px 0;">
                  <div style="color:#aaa;font-size:0.95rem;text-align:center;">
                    StudyFlow &middot; Your smart studyplanner
                  </div>
                </div>
                """.formatted(resetUrl);

            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}