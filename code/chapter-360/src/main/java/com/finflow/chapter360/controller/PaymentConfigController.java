package com.finflow.chapter360.controller;

import com.finflow.chapter360.config.PaymentGatewayProperties;
import com.finflow.chapter360.model.DiscoveredInstance;
import com.finflow.chapter360.model.FeeCalculationResult;
import com.finflow.chapter360.service.DynamicPaymentRateService;
import com.finflow.chapter360.service.ServiceDiscoveryManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PaymentConfigController {

    private final DynamicPaymentRateService feeService;
    private final ServiceDiscoveryManager discoveryManager;

    public PaymentConfigController(DynamicPaymentRateService feeService, ServiceDiscoveryManager discoveryManager) {
        this.feeService = feeService;
        this.discoveryManager = discoveryManager;
    }

    @GetMapping("/config/properties")
    public ResponseEntity<Map<String, Object>> getProperties() {
        PaymentGatewayProperties props = feeService.getProperties();
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("transactionFeePercent", props.getTransactionFeePercent());
        map.put("fixedFeeCents", props.getFixedFeeCents());
        map.put("instantSettlementEnabled", props.isInstantSettlementEnabled());
        map.put("maxDailyVolume", props.getMaxDailyVolume());
        map.put("partnerGatewayUrl", props.getPartnerGatewayUrl());
        map.put("environmentTier", props.getEnvironmentTier());
        return ResponseEntity.ok(map);
    }

    @GetMapping("/config/fees/calculate")
    public ResponseEntity<FeeCalculationResult> calculateFee(
            @RequestParam(defaultValue = "TX-DEFAULT-001") String txId,
            @RequestParam(defaultValue = "100.00") BigDecimal amount) {
        FeeCalculationResult result = feeService.calculateTransactionFee(txId, amount);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/config/update-fees")
    public ResponseEntity<Map<String, Object>> updateFeeRates(
            @RequestParam BigDecimal percent,
            @RequestParam(defaultValue = "30") int fixedCents) {
        feeService.getProperties().setTransactionFeePercent(percent);
        feeService.getProperties().setFixedFeeCents(fixedCents);

        return ResponseEntity.ok(Map.of(
                "status", "UPDATED",
                "message", "Dynamic properties updated successfully via @RefreshScope simulation",
                "newFeePercent", percent,
                "newFixedCents", fixedCents
        ));
    }

    @GetMapping("/discovery/services")
    public ResponseEntity<Map<String, List<DiscoveredInstance>>> getDiscoveredServices() {
        return ResponseEntity.ok(discoveryManager.getAllServices());
    }

    @GetMapping("/discovery/choose/{serviceId}")
    public ResponseEntity<DiscoveredInstance> chooseInstance(@PathVariable String serviceId) {
        return discoveryManager.chooseInstance(serviceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
