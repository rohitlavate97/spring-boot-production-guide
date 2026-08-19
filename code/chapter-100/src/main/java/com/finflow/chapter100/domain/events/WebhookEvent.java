package com.finflow.chapter100.domain.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "eventType"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ChargeSucceededEvent.class, name = "CHARGE_SUCCEEDED"),
    @JsonSubTypes.Type(value = RefundCreatedEvent.class, name = "REFUND_CREATED")
})
public interface WebhookEvent {
    String eventId();
}
