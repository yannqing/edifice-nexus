package com.qsy.edifice.service.support;

import com.qsy.edifice.domain.model.OutputAllocationContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OutputAllocationCalculator {

    private static final BigDecimal BD_100 = new BigDecimal("100");
    private static final int RATE_SCALE = 4;

    private OutputAllocationCalculator() {
    }

    public static OutputAllocationContext calculate(BigDecimal totalAmount,
                                                    Long ruleVersionId,
                                                    Integer ruleVersionNo,
                                                    BigDecimal employeePoolRate,
                                                    BigDecimal companyBaseRate,
                                                    List<WorkRule> rules) {
        BigDecimal total = money(totalAmount);
        List<WorkRule> sortedRules = rules.stream()
                .sorted(Comparator.comparing(WorkRule::workType))
                .toList();

        BigDecimal employeePool = percentOf(total, employeePoolRate);
        BigDecimal companyBase = total.subtract(employeePool).setScale(2, RoundingMode.HALF_UP);
        BigDecimal allocatedGross = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<OutputAllocationContext.WorkPool> pools = new ArrayList<>();

        for (int index = 0; index < sortedRules.size(); index++) {
            WorkRule rule = sortedRules.get(index);
            BigDecimal grossRate = employeePoolRate.multiply(rule.workWeight())
                    .divide(BD_100, RATE_SCALE, RoundingMode.HALF_UP);
            BigDecimal grossAmount = index == sortedRules.size() - 1
                    ? employeePool.subtract(allocatedGross).setScale(2, RoundingMode.HALF_UP)
                    : percentOf(employeePool, rule.workWeight());
            allocatedGross = allocatedGross.add(grossAmount);

            BigDecimal projectRate = rule.projectCapRate() == null
                    ? grossRate
                    : grossRate.min(rule.projectCapRate()).setScale(RATE_SCALE, RoundingMode.HALF_UP);
            BigDecimal projectAmount = rule.projectCapRate() == null
                    || rule.projectCapRate().compareTo(grossRate) >= 0
                    ? grossAmount
                    : percentOf(total, projectRate);
            BigDecimal companyAmount = grossAmount.subtract(projectAmount).setScale(2, RoundingMode.HALF_UP);
            BigDecimal companyRate = grossRate.subtract(projectRate).setScale(RATE_SCALE, RoundingMode.HALF_UP);

            pools.add(new OutputAllocationContext.WorkPool(
                    rule.workType(),
                    rule.workWeight().setScale(RATE_SCALE, RoundingMode.HALF_UP),
                    grossRate,
                    grossAmount,
                    projectRate,
                    projectAmount,
                    companyRate,
                    companyAmount
            ));
        }

        BigDecimal projectPool = pools.stream()
                .map(OutputAllocationContext.WorkPool::getProjectAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal workTransfer = pools.stream()
                .map(OutputAllocationContext.WorkPool::getCompanyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new OutputAllocationContext(
                ruleVersionId,
                ruleVersionNo,
                employeePoolRate.setScale(RATE_SCALE, RoundingMode.HALF_UP),
                companyBaseRate.setScale(RATE_SCALE, RoundingMode.HALF_UP),
                employeePool,
                companyBase,
                workTransfer,
                projectPool,
                pools
        );
    }

    public static OutputAllocationContext merge(BigDecimal totalAmount,
                                                List<OutputAllocationContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            throw new IllegalArgumentException("allocation contexts must not be empty");
        }

        OutputAllocationContext first = contexts.get(0);
        Map<Integer, BigDecimal[]> totalsByWorkType = new LinkedHashMap<>();
        for (OutputAllocationContext context : contexts) {
            if (!java.util.Objects.equals(first.getRuleVersionId(), context.getRuleVersionId())) {
                throw new IllegalStateException("allocation rule changed while calculating; retry required");
            }
            for (OutputAllocationContext.WorkPool pool : context.getWorkPools()) {
                BigDecimal[] totals = totalsByWorkType.computeIfAbsent(
                        pool.getWorkType(), key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                totals[0] = totals[0].add(pool.getGrossAmount());
                totals[1] = totals[1].add(pool.getProjectAmount());
                totals[2] = totals[2].add(pool.getCompanyAmount());
            }
        }

        BigDecimal total = money(totalAmount);
        BigDecimal employeePool = sum(contexts, OutputAllocationContext::getEmployeePoolAmount);
        BigDecimal companyBase = sum(contexts, OutputAllocationContext::getCompanyBaseAmount);
        BigDecimal workTransfer = sum(contexts, OutputAllocationContext::getWorkTransferAmount);
        BigDecimal projectPool = sum(contexts, OutputAllocationContext::getProjectPoolAmount);

        List<OutputAllocationContext.WorkPool> pools = totalsByWorkType.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    BigDecimal grossAmount = money(entry.getValue()[0]);
                    BigDecimal projectAmount = money(entry.getValue()[1]);
                    BigDecimal companyAmount = money(entry.getValue()[2]);
                    return new OutputAllocationContext.WorkPool(
                            entry.getKey(),
                            rateOf(grossAmount, employeePool),
                            rateOf(grossAmount, total),
                            grossAmount,
                            rateOf(projectAmount, total),
                            projectAmount,
                            rateOf(companyAmount, total),
                            companyAmount
                    );
                })
                .toList();

        return new OutputAllocationContext(
                first.getRuleVersionId(),
                first.getRuleVersionNo(),
                first.getEmployeePoolRate(),
                first.getCompanyBaseRate(),
                employeePool,
                companyBase,
                workTransfer,
                projectPool,
                pools
        );
    }

    private static BigDecimal sum(List<OutputAllocationContext> contexts,
                                  java.util.function.Function<OutputAllocationContext, BigDecimal> getter) {
        return contexts.stream()
                .map(getter)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rateOf(BigDecimal amount, BigDecimal baseAmount) {
        if (baseAmount == null || baseAmount.signum() == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return amount.multiply(BD_100).divide(baseAmount, RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentOf(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).divide(BD_100, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2) : amount.setScale(2, RoundingMode.HALF_UP);
    }

    public record WorkRule(Integer workType, BigDecimal workWeight, BigDecimal projectCapRate) {
    }
}
