package com.finflow.chapter070.correct;

import com.finflow.chapter070.domain.MerchantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

public class MerchantSecurityInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String merchantId = request.getHeader("X-Merchant-ID");
        if (merchantId != null && !merchantId.isBlank()) {
            MerchantContext context = new MerchantContext(merchantId, "PREMIUM", "api-key", "US");
            MerchantContextHolder.set(context);
            MDC.put("merchantId", merchantId);
        }
        return true;
    }

    // Correct: afterCompletion is guaranteed to run even if an exception occurs in the controller or views.
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MerchantContextHolder.clear();
        MDC.remove("merchantId");
    }
}
