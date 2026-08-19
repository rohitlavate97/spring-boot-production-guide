package com.finflow.chapter070.incorrect;

import com.finflow.chapter070.domain.MerchantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

public class ThreadLocalLeakingInterceptorIncorrect implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String merchantId = request.getHeader("X-Merchant-ID");
        if (merchantId != null) {
            MerchantContext context = new MerchantContext(merchantId, "STANDARD", "dummy-key", "US");
            MerchantContextHolderIncorrect.set(context);
        }
        return true;
    }

    // Incorrect: postHandle is NOT called if the controller throws an exception.
    // This will cause the ThreadLocal context to leak in the Tomcat thread pool.
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        MerchantContextHolderIncorrect.clear();
    }
}
