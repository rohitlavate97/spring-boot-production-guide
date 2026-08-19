package com.finflow.chapter070.incorrect;

public class MerchantContextHolderIncorrect {
    private static final ThreadLocal<com.finflow.chapter070.domain.MerchantContext> HOLDER = new ThreadLocal<>();

    public static void set(com.finflow.chapter070.domain.MerchantContext context) {
        HOLDER.set(context);
    }

    public static com.finflow.chapter070.domain.MerchantContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
