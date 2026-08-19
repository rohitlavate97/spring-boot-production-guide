package com.finflow.chapter100.incorrect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incorrect/events")
public class NewObjectMapperPerRequestControllerIncorrect {

    @PostMapping
    public String parseEvent(@RequestBody String rawJson) {
        try {
            // INCORRECT: Allocates new ObjectMapper on every request handler call
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(rawJson).get("eventId").asText();
        } catch (Exception e) {
            return "error";
        }
    }
}
