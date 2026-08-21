package com.finflow.chapter390.unit;

import com.finflow.chapter390.model.PerformanceBenchmarkReport;
import com.finflow.chapter390.service.OptimizedFeeCalculatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class OptimizedFeeCalculatorUnitTest {

    private OptimizedFeeCalculatorService feeCalculator;

    @BeforeEach
    void setUp() {
        feeCalculator = new OptimizedFeeCalculatorService();
    }

    @Test
    void testFeeCalculationsAreIdenticalAndAccurate() {
        BigDecimal amount1 = BigDecimal.valueOf(100.00);
        // 2.5% of 100 + 0.30 = 2.80
        BigDecimal feeSync1 = feeCalculator.calculateFeeSynchronized(amount1);
        BigDecimal feeLockFree1 = feeCalculator.calculateFeeLockFree(amount1);

        assertThat(feeSync1).isEqualByComparingTo("2.80");
        assertThat(feeLockFree1).isEqualByComparingTo("2.80");

        BigDecimal amount2 = BigDecimal.valueOf(250.00);
        // 2.5% of 250 = 6.25 + 0.30 = 6.55
        BigDecimal feeSync2 = feeCalculator.calculateFeeSynchronized(amount2);
        BigDecimal feeLockFree2 = feeCalculator.calculateFeeLockFree(amount2);

        assertThat(feeSync2).isEqualByComparingTo("6.55");
        assertThat(feeLockFree2).isEqualByComparingTo("6.55");
    }

    @Test
    void testBenchmarkExecutesAndMeasuresSpeedup() throws InterruptedException {
        PerformanceBenchmarkReport report = feeCalculator.runBenchmark(1000, 4);

        assertThat(report).isNotNull();
        assertThat(report.getIterations()).isEqualTo(1000);
        assertThat(report.getConcurrency()).isEqualTo(4);
        assertThat(report.getSynchronizedDurationMs()).isGreaterThan(0);
        assertThat(report.getLockFreeDurationMs()).isGreaterThan(0);
        assertThat(report.getLockFreeOpsPerSec()).isGreaterThan(0);
    }
}
