package com.qsy.edifice.service;

import com.qsy.edifice.domain.model.OutputAllocationContext;
import com.qsy.edifice.service.support.OutputAllocationCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutputAllocationCalculatorTests {

    @Test
    void splitsFullSettlementSecondStageIntoIndependentWorkPools() {
        OutputAllocationContext result = OutputAllocationCalculator.calculate(
                new BigDecimal("10920"),
                1L,
                1,
                new BigDecimal("40"),
                new BigDecimal("60"),
                List.of(
                        new OutputAllocationCalculator.WorkRule(0, new BigDecimal("15"), new BigDecimal("4")),
                        new OutputAllocationCalculator.WorkRule(1, new BigDecimal("70"), null),
                        new OutputAllocationCalculator.WorkRule(2, new BigDecimal("15"), new BigDecimal("4"))
                )
        );

        assertMoney("4368.00", result.getEmployeePoolAmount());
        assertMoney("6552.00", result.getCompanyBaseAmount());
        assertMoney("3931.20", result.getProjectPoolAmount());
        assertMoney("436.80", result.getWorkTransferAmount());

        assertPool(result.getWorkPools().get(0), "655.20", "436.80", "218.40");
        assertPool(result.getWorkPools().get(1), "3057.60", "3057.60", "0.00");
        assertPool(result.getWorkPools().get(2), "655.20", "436.80", "218.40");

        BigDecimal invariant = result.getCompanyBaseAmount()
                .add(result.getWorkTransferAmount())
                .add(result.getProjectPoolAmount());
        assertMoney("10920.00", invariant);
    }

    @Test
    void mergesCurrentStageAndSourceStageAdjustmentWithoutChangingRoleOwnership() {
        OutputAllocationContext currentStage = calculate(
                "10000", "15", "70", "15");
        OutputAllocationContext sourceAdjustment = calculate(
                "1000", "70", "10", "20");

        OutputAllocationContext result = OutputAllocationCalculator.merge(
                new BigDecimal("11000"), List.of(currentStage, sourceAdjustment));

        assertMoney("4400.00", result.getEmployeePoolAmount());
        assertMoney("6600.00", result.getCompanyBaseAmount());
        assertMoney("3720.00", result.getProjectPoolAmount());
        assertMoney("680.00", result.getWorkTransferAmount());

        assertPool(result.getWorkPools().get(0), "880.00", "440.00", "440.00");
        assertPool(result.getWorkPools().get(1), "2840.00", "2840.00", "0.00");
        assertPool(result.getWorkPools().get(2), "680.00", "440.00", "240.00");
    }

    private OutputAllocationContext calculate(String amount,
                                              String managementWeight,
                                              String baseWeight,
                                              String wisdomWeight) {
        return OutputAllocationCalculator.calculate(
                new BigDecimal(amount),
                1L,
                1,
                new BigDecimal("40"),
                new BigDecimal("60"),
                List.of(
                        new OutputAllocationCalculator.WorkRule(0, new BigDecimal(managementWeight), new BigDecimal("4")),
                        new OutputAllocationCalculator.WorkRule(1, new BigDecimal(baseWeight), null),
                        new OutputAllocationCalculator.WorkRule(2, new BigDecimal(wisdomWeight), new BigDecimal("4"))
                )
        );
    }

    private void assertPool(OutputAllocationContext.WorkPool pool,
                            String gross,
                            String project,
                            String company) {
        assertMoney(gross, pool.getGrossAmount());
        assertMoney(project, pool.getProjectAmount());
        assertMoney(company, pool.getCompanyAmount());
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
