package com.finflow.chapter070.correct;

import com.finflow.chapter070.domain.MerchantContext;

public class MerchantContextHolder {
    private static final ThreadLocal<MerchantContext> HOLDER = new ThreadLocal<>();

    public static void set(MerchantContext context) {
        HOLDER.set(context);
    }

    public static MerchantContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
