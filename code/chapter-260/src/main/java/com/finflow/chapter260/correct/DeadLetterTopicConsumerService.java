package com.finflow.chapter260.correct;

import com.finflow.chapter260.domain.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DeadLetterTopicConsumerService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterTopicConsumerService.class);

    private final List<PaymentEvent> dltEvents = Collections.synchronizedList(new ArrayList<>());

    @KafkaListener(topics = "payment.events.DLT", groupId = "finflow-dlt-audit-group", containerFactory = "kafkaListenerContainerFactory")
    public void consumeDlt(PaymentEvent event, Acknowledgment ack) {
        log.error("Dead Letter Topic received poison pill event: {}", event);
        dltEvents.add(event);
        ack.acknowledge();
    }

    public List<PaymentEvent> getDltEvents() {
        return Collections.unmodifiableList(dltEvents);
    }

    public void clear() {
        dltEvents.clear();
    }
}
