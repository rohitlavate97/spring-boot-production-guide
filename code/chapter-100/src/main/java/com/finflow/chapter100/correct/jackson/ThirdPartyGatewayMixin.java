package com.finflow.chapter100.correct.jackson;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface ThirdPartyGatewayMixin {
    @JsonIgnore
    String getRawPan();
}
