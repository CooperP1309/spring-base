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
    /*  Sending of Account Verification Email
     *
     *  Using thymleaf TemplateEngine, this unit
     *  handles the packing and sending of a verification email
     *  with HTML.
     */
    public void sendVerificationEmail(String to, String verificationCode) throws Exception
    {
        // Build the verificaiton link
        String base = verificationBaseUrl.endsWith("/")
                ? verificationBaseUrl.substring(0, verificationBaseUrl.length() - 1)
                : verificationBaseUrl;
        String verificationLink = base + "/auth/verify/" + verificationCode;

        Map<String, Object> variables = new HashMap<>();
        variables.put("verificationLink", verificationLink);
        
        // Build the template with Thymeleaf's Template Engine
        Context context = new Context();
        context.setVariables(variables);
        String htmlContent = templateEngine.process("email/verify-email", context);

        // Init and send off message
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setTo(to);
        helper.setSubject("Verify Your Email");
        helper.setText(htmlContent, true);

        mailSender.send(message);
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
}
