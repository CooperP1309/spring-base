package com.anticipate.listr.authentication.services;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.thymeleaf.TemplateEngine;
import org.springframework.mail.javamail.MimeMessageHelper;   
import jakarta.mail.internet.MimeMessage;
import org.thymeleaf.context.Context;
import java.util.Map;
import java.util.HashMap;
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

    private TemplateEngine templateEngine;

    public SMTPService(JavaMailSender mailSender, TemplateEngine templateEngine) 
    {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Async
    /*  Sending of Password Reset Email
     *
     *  Using thymleaf TemplateEngine, this unit
     *  handles the packing and sending of a password reset email
     *  with HTML.
     */
    public void sendPasswordResetEmail(String to, String verificationCode) throws Exception
    {
        // Build the password reset link
        String base = verificationBaseUrl.endsWith("/")
        ? verificationBaseUrl.substring(0, verificationBaseUrl.length() - 1)
        : verificationBaseUrl;
                
        String passwordResetLink = base + "/auth/reset-password/" + verificationCode;
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("passwordResetLink", passwordResetLink);
        
        // Build the template with Thymeleaf's Template Engine
        Context context = new Context();
        context.setVariables(variables);
        String htmlContent = templateEngine.process("email/password-reset", context);

        // Init and send off message
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setTo(to);
        helper.setSubject("Password Reset");
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }













/* LEGACY IMPLEMENTATION */

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


    /* Test function for template based emails
     *
     */
    public void sendTestTemplateEmail(String to, String subject, Map<String, Object> variables) throws Exception
    {
        // 1. Process Template
        Context context = new Context();
        context.setVariables(variables);
        String htmlContent = templateEngine.process("email/email-template", context);

        // 2. Build MimeMessage
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true); // true indicates HTML content

        // 3. Send
        mailSender.send(message);
    }
}
