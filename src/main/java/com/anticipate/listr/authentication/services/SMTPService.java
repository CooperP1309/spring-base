package com.anticipate.listr.authentication.services;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SMTPService 
{
    @Value("${smtp.sender.email}")
    private String senderEmail;

    @Value("${smtp.verification.base-url}")
    private String verificationBaseUrl;

    private JavaMailSender mailSender;

    public SMTPService(JavaMailSender mailSender) 
    {
        this.mailSender = mailSender;
    }

    @Async
    /*  Wrapper function for sendEmail()
     *
     *  This function wraps sendEmail() with the intention of
     *  building a body and subject specific to sending verification
     *  links. 
     */
    public void sendVerificationLink(String verificationCode, String receivingEmail) 
    {
        
        String base = verificationBaseUrl.endsWith("/")
                ? verificationBaseUrl.substring(0, verificationBaseUrl.length() - 1)
                : verificationBaseUrl;
        String verificationLink = base + "/auth/verify/" + verificationCode;

        String subject = "Verify your account";
        String body =   "Thank you for signing up!\n\n" +
                        "To complete your account setup, click the verification link below:\n\n" +
                        verificationLink;

        sendEmail(subject, body, receivingEmail);
    }

    @Async
    /*  Another wrapper function for sendEmail()
     *
     *  This function wraps sendEmail() with the intention of
     *  building a body and subject specific to sending password
     *  reset links. The function is called asynchronously and thus
     *  must catch and handle exceptions interally rather than allowing
     *  them to propogate.
     */
    public void sendPasswordResetLink(String verificationCode, String receivingEmail) 
    {
        String base = verificationBaseUrl.endsWith("/")
                ? verificationBaseUrl.substring(0, verificationBaseUrl.length() - 1)
                : verificationBaseUrl;
                
        String verificationLink = base + "/auth/reset-password/" + verificationCode;

        String subject = "Reset your password";
        String body =   "IGNORE IF YOU DIDN'T REQUEST A PASSWORD RESET!\n\n" +
                        "To reset your password, click the link below:\n\n" +
                        verificationLink;

        sendEmail(subject, body, receivingEmail);
    }

    /*  Core email sending unit.
     *
     *  This is core interface for sending emails. It relies
     *  on SimpleMailMessage and thus email formatting is very
     *  limited.
     */
    public void sendEmail(String subject, String body, String receivingEmail) 
    {    
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(this.senderEmail);
        message.setTo(receivingEmail);
        message.setSubject(subject);
        message.setText(body);

        try
        {
            mailSender.send(message);
        }
        catch (Exception e)
        {
            log.error("Sending email: {}", e.getMessage());
        }
    }
}
