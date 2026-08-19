package com.finflow.chapter070.correct;

import com.finflow.chapter070.domain.MerchantContext;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class MerchantContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentMerchant.class)
                && MerchantContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        MerchantContext context = MerchantContextHolder.get();
        if (context == null) {
            String merchantId = webRequest.getHeader("X-Merchant-ID");
            if (merchantId != null && !merchantId.isBlank()) {
                 return new MerchantContext(merchantId, "STANDARD", "header-key", "US");
            }
            throw new IllegalStateException("MerchantContext is missing. Check headers and interceptor order.");
        }
        return context;
    }
}
