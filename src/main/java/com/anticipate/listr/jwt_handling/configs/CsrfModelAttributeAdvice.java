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
/*  NOTE FOR FUTURE ME:
 *  How does @ControllerAdvice work here?
 *  
 *  Controller Advice includes @Component, so on startup, this
 *  class is automatically created as a bean.
 * 
 *  On each request, Spring uses "RequestMappingHandlerAdapter" to automatically
 *  call this functio and populate the model BEFORE any of the controllers are called.
 *  YES, this preceeds the execution of any controllers.
 */
public class CsrfModelAttributeAdvice
{
    @ModelAttribute(CsrfProtectionFilter.REQUEST_ATTRIBUTE)
    public String csrfToken(HttpServletRequest request)
    {
        Object token = request.getAttribute(CsrfProtectionFilter.REQUEST_ATTRIBUTE);
        return token == null ? null : token.toString();
    }
    /*  As you already know, what ever is returned from this function is mapped
     *  to the string in "@ModelAttribute(<model-name-string)"
     *  
     *  By memory, CsrfProtectionFilter.REQUEST_ATTRIBUTE maps to "_csrf", so
     *  that's what will be populated in the html form by what this function returns.
     */

}
