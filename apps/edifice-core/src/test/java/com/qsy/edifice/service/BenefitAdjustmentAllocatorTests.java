package com.qsy.edifice.service;

import com.qsy.edifice.service.support.BenefitAdjustmentAllocator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BenefitAdjustmentAllocatorTests {

    @Test
    void calculatesPersonTargetFromHistoricalPoolAndPersonSnapshots() {
        BigDecimal target = BenefitAdjustmentAllocator.calculatePersonTarget(
                new BigDecimal("800.00"),
                new BigDecimal("13.8958"),
                new BigDecimal("100"),
                new BigDecimal("100"));

        assertMoney("111.17", target);
    }

    @Test
    void creditsTheOriginalPeopleAndDoesNotRedistributeByCurrentRatios() {
        var result = BenefitAdjustmentAllocator.allocate(List.of(
                pending(1L, 101L, "2000.00"),
                pending(2L, 102L, "3000.00")
        ), Map.of());

        assertMoney("2000.00", result.adjustments().get(0).appliedAmount());
        assertEquals(101L, result.adjustments().get(0).source().userId());
        assertMoney("3000.00", result.adjustments().get(1).appliedAmount());
        assertEquals(102L, result.adjustments().get(1).source().userId());
        assertMoney("5000.00", result.appliedTotal());
    }

    @Test
    void settlesMultipleHistoricalStagesInOneBatch() {
        var result = BenefitAdjustmentAllocator.allocate(List.of(
                pending(1L, 101L, "2000.00"),
                pending(2L, 101L, "1500.00")
        ), Map.of());

        assertMoney("3500.00", result.appliedTotal());
        assertMoney("0.00", result.remainingTotal());
    }

    @Test
    void capsNegativeAdjustmentAtCurrentPersonAmountAndCarriesTheRest() {
        var result = BenefitAdjustmentAllocator.allocate(
                List.of(pending(1L, 101L, "-5000.00")),
                Map.of(101L, new BigDecimal("3000.00")));

        assertMoney("-3000.00", result.appliedTotal());
        assertMoney("-2000.00", result.remainingTotal());
    }

    @Test
    void continuedDebitCanBeSettledByTheNextStageAmount() {
        var result = BenefitAdjustmentAllocator.allocate(
                List.of(pending(1L, 101L, "-2000.00")),
                Map.of(101L, new BigDecimal("2500.00")));

        assertMoney("-2000.00", result.appliedTotal());
        assertMoney("0.00", result.remainingTotal());
    }

    @Test
    void positiveAdjustmentCanFundANegativeAdjustmentForTheSamePerson() {
        var result = BenefitAdjustmentAllocator.allocate(List.of(
                pending(1L, 101L, "1000.00"),
                pending(2L, 101L, "-1600.00")
        ), Map.of(101L, new BigDecimal("200.00")));

        assertMoney("-200.00", result.appliedTotal());
        assertMoney("-400.00", result.remainingTotal());
    }

    private BenefitAdjustmentAllocator.PendingAdjustment pending(Long sourceDistributionId,
                                                                 Long userId,
                                                                 String amount) {
        BigDecimal value = new BigDecimal(amount);
        return new BenefitAdjustmentAllocator.PendingAdjustment(
                sourceDistributionId, 10L + sourceDistributionId, 20L, "阶段",
                userId, 0, value, BigDecimal.ZERO, value);
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
