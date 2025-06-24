package com.studyflow.studyplanner.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply.studyflow@gmail.com"); // sender email address
        message.setTo(to);
        message.setSubject("Password Reset Request");
        message.setText("To reset your password, click the link: "
                + "http://localhost:8080/reset-password?token=" + token); // TODO: Change localhost for production
        mailSender.send(message);
    }
}