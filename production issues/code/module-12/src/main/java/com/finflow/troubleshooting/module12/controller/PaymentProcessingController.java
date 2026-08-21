package com.finflow.troubleshooting.module12.controller;

import com.finflow.troubleshooting.module12.dto.CreditAssessmentResult;
import com.finflow.troubleshooting.module12.service.CreditAssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentProcessingController {

    private final CreditAssessmentService creditAssessmentService;

    public PaymentProcessingController(CreditAssessmentService creditAssessmentService) {
        this.creditAssessmentService = creditAssessmentService;
    }

    @GetMapping("/assess-credit")
    public ResponseEntity<CreditAssessmentResult> assessCredit(@RequestParam String customerId,
                                                               @RequestParam(defaultValue = "false") boolean simulateFailure,
                                                               @RequestParam(defaultValue = "false") boolean simulateTimeout) {
        CreditAssessmentResult result = creditAssessmentService.evaluateCredit(customerId, simulateFailure, simulateTimeout);
        return ResponseEntity.ok(result);
    }
}
