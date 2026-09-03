package com.anticipate.listr.jwt_handling.services;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SMTPService 
{
    @Value("${smtp.sender.email}")
    private String senderEmail;

    private JavaMailSender mailSender;

    public SMTPService(JavaMailSender mailSender) 
    {
        this.mailSender = mailSender;
    }

    /*  Wrapper function for sendEmail()
     *
     *  This function wraps sendEmail() with the intention of
     *  building a body and subject specific to sending verification
     *  links. 
     */
    public String sendVerificationLink(String verificationCode, String receivingEmail) 
    {
        
        String verificationLink = "http://localhost:8005/auth/verify/" + verificationCode;

        String subject = "Verify your account";
        String body =   "Thank you for signing up!\n\n" +
                        "To complete your account setup, click the verification link below:\n\n" +
                        verificationLink;

        return sendEmail(subject, body, receivingEmail);
    }

    /*  Core email sending unit.
     *
     *  This is core interface for sending emails. It relies
     *  on SimpleMailMessage and thus email formatting is very
     *  limited.
     */
    public String sendEmail(String subject, String body, String receivingEmail) 
    {
                
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(this.senderEmail);
        message.setTo(receivingEmail);
        message.setSubject(subject);
        message.setText(body);

        try 
        {
            mailSender.send(message);
            return "Success"; 
        }
        catch (Exception ex) 
        {
            return "Failure";
        }
    }
}
