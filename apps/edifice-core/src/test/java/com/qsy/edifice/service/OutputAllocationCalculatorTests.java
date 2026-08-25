package com.qsy.edifice.service;

import com.qsy.edifice.domain.model.OutputAllocationContext;
import com.qsy.edifice.service.support.OutputAllocationCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutputAllocationCalculatorTests {

    @Test
    void usesSystemStageWeightAndTableAggregateSplitRates() {
        OutputAllocationContext result = OutputAllocationCalculator.calculate(
                new BigDecimal("4000"),
                3L,
                3,
                new BigDecimal("40"),
                new BigDecimal("60"),
                List.of(
                        new OutputAllocationCalculator.StageWeightRule(0, new BigDecimal("70")),
                        new OutputAllocationCalculator.StageWeightRule(1, new BigDecimal("10")),
                        new OutputAllocationCalculator.StageWeightRule(2, new BigDecimal("20"))
                ),
                List.of(
                        new OutputAllocationCalculator.PoolRateRule(0, new BigDecimal("8.06"), new BigDecimal("4"), new BigDecimal("4.06")),
                        new OutputAllocationCalculator.PoolRateRule(1, new BigDecimal("20.28"), new BigDecimal("20.28"), BigDecimal.ZERO),
                        new OutputAllocationCalculator.PoolRateRule(2, new BigDecimal("11.66"), new BigDecimal("4"), new BigDecimal("7.66"))
                )
        );

        assertMoney("1600.00", result.getEmployeePoolAmount());
        assertMoney("2400.00", result.getCompanyBaseAmount());
        assertMoney("825.61", result.getProjectPoolAmount());
        assertMoney("774.39", result.getWorkTransferAmount());

        assertPool(result.getWorkPools().get(0), "1120.00", "555.83", "564.17");
        assertPool(result.getWorkPools().get(1), "160.00", "160.00", "0.00");
        assertPool(result.getWorkPools().get(2), "320.00", "109.78", "210.22");
        assertEquals(0, new BigDecimal("13.8958").compareTo(result.getWorkPools().get(0).getProjectRate()));
        assertEquals(0, new BigDecimal("4.0000").compareTo(result.getWorkPools().get(1).getProjectRate()));
        assertEquals(0, new BigDecimal("2.7444").compareTo(result.getWorkPools().get(2).getProjectRate()));

        BigDecimal invariant = result.getCompanyBaseAmount()
                .add(result.getWorkTransferAmount())
                .add(result.getProjectPoolAmount());
        assertMoney("4000.00", invariant);
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
        assertMoney("3510.00", result.getProjectPoolAmount());
        assertMoney("890.00", result.getWorkTransferAmount());

        assertPool(result.getWorkPools().get(0), "880.00", "436.73", "443.27");
        assertPool(result.getWorkPools().get(1), "2840.00", "2840.00", "0.00");
        assertPool(result.getWorkPools().get(2), "680.00", "233.27", "446.73");
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
                        new OutputAllocationCalculator.StageWeightRule(0, new BigDecimal(managementWeight)),
                        new OutputAllocationCalculator.StageWeightRule(1, new BigDecimal(baseWeight)),
                        new OutputAllocationCalculator.StageWeightRule(2, new BigDecimal(wisdomWeight))
                ),
                List.of(
                        new OutputAllocationCalculator.PoolRateRule(0, new BigDecimal("8.06"), new BigDecimal("4"), new BigDecimal("4.06")),
                        new OutputAllocationCalculator.PoolRateRule(1, new BigDecimal("20.28"), new BigDecimal("20.28"), BigDecimal.ZERO),
                        new OutputAllocationCalculator.PoolRateRule(2, new BigDecimal("11.66"), new BigDecimal("4"), new BigDecimal("7.66"))
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
