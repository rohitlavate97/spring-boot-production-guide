package com.finflow.chapter100.incorrect;

public class StrictUnknownPropertyEventIncorrect {
    private String eventId;

    // Standard getters/setters without @JsonIgnoreProperties(ignoreUnknown = true)
    // If Jackson is configured globally with FAIL_ON_UNKNOWN_PROPERTIES=true, this fails.

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
}
