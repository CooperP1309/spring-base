package com.anticipate.listr.jwt_handling.configs;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/*  Relays the token CsrfProtectionFilter already resolved for this request
 *  into every view's model, so templates can render it as
 *  <input type="hidden" name="_csrf" th:value="${csrfToken}"/> without each
 *  controller method wiring it in manually.
 */
@ControllerAdvice
public class CsrfModelAttributeAdvice
{
    @ModelAttribute(CsrfProtectionFilter.REQUEST_ATTRIBUTE)
    public String csrfToken(HttpServletRequest request)
    {
        Object token = request.getAttribute(CsrfProtectionFilter.REQUEST_ATTRIBUTE);
        return token == null ? null : token.toString();
    }
}
