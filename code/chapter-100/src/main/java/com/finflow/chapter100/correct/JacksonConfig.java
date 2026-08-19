package com.finflow.chapter100.correct;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.finflow.chapter100.correct.jackson.ThirdPartyGatewayMixin;
import com.finflow.chapter100.domain.ThirdPartyGatewayRawResponse;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.modulesToInstall(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            builder.mixIn(ThirdPartyGatewayRawResponse.class, ThirdPartyGatewayMixin.class);
        };
    }
}
