package com.finflow.chapter240.controller;

import com.finflow.chapter240.model.TokenIntrospectionResponse;
import com.finflow.chapter240.service.TokenIntrospectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oauth2")
public class OAuth2IntrospectionController {

    private final TokenIntrospectionService introspectionService;

    public OAuth2IntrospectionController(TokenIntrospectionService introspectionService) {
        this.introspectionService = introspectionService;
    }

    @PostMapping("/introspect")
    public ResponseEntity<TokenIntrospectionResponse> introspect(@RequestParam("token") String token) {
        TokenIntrospectionResponse response = introspectionService.introspect(token);
        return ResponseEntity.ok(response);
    }
}
