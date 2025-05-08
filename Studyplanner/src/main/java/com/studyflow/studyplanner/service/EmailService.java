package com.studyflow.studyplanner.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendPasswordResetEmail(String to, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply.studyflow@gmail.com"); // here EMAIL
        message.setTo(to);
        message.setSubject("Password Reset Request");
        message.setText("To reset your password, click the link: "
                + "localhost:8080/reset-password?token=" + token); // here change localhost
        mailSender.send(message);
    }
}