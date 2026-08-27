package com.anticipate.listr.jwt_handling.services;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SMTPService {

    @Value("${smtp.sender.email}")
    private String senderEmail;

    @Value("${smtp.receiver.email}")
    private String receiverEmail;

    private JavaMailSender mailSender;

    public SMTPService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public String sendVerificationLink(String verificationCode, String receivingEmail) {
        
        String verificationLink = "http://localhost:8005/auth/verify/" + verificationCode;

        String subject = "Verify your account";
        String body =   "Thank you for signing up!\n\n" +
                        "To complete your account setup, click the verification link below:\n\n" +
                        verificationLink;

        System.out.println("[SMTPService] Sending verification link to " + receivingEmail + "\n");

        return sendEmail(subject, body, receivingEmail);
    }

    public String sendEmail(String subject, String body, String receivingEmail) {
                
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(this.senderEmail);
        message.setTo(receivingEmail);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            
            return "Success"; 
        } catch (Exception ex) {
            System.out.println("Failed to send: " + ex.getMessage());
            
            return "Failure";
        }
    }
}
