package com.finflow.chapter270.unit;

import com.rabbitmq.client.Channel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TestChannelStub implements InvocationHandler {

    private final AtomicLong ackDeliveryTag = new AtomicLong(-1);
    private final AtomicBoolean ackMultiple = new AtomicBoolean(false);
    private final AtomicLong nackDeliveryTag = new AtomicLong(-1);
    private final AtomicBoolean nackMultiple = new AtomicBoolean(false);
    private final AtomicBoolean nackRequeue = new AtomicBoolean(false);

    public static Channel createMockChannel(TestChannelStub stub) {
        return (Channel) Proxy.newProxyInstance(
                Channel.class.getClassLoader(),
                new Class<?>[]{Channel.class},
                stub
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("basicAck".equals(method.getName()) && args != null && args.length == 2) {
            ackDeliveryTag.set((Long) args[0]);
            ackMultiple.set((Boolean) args[1]);
            return null;
        }
        if ("basicNack".equals(method.getName()) && args != null && args.length == 3) {
            nackDeliveryTag.set((Long) args[0]);
            nackMultiple.set((Boolean) args[1]);
            nackRequeue.set((Boolean) args[2]);
            return null;
        }
        return null;
    }

    public long getAckDeliveryTag() { return ackDeliveryTag.get(); }
    public boolean isAckMultiple() { return ackMultiple.get(); }
    public long getNackDeliveryTag() { return nackDeliveryTag.get(); }
    public boolean isNackMultiple() { return nackMultiple.get(); }
    public boolean isNackRequeue() { return nackRequeue.get(); }
}
