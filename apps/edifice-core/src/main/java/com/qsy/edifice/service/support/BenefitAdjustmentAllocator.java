package com.qsy.edifice.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies signed person adjustments without allowing a person's current payout to become negative. */
public final class BenefitAdjustmentAllocator {

    private BenefitAdjustmentAllocator() {
    }

    public static BigDecimal calculatePersonTarget(BigDecimal stageBenefitDelta,
                                                   BigDecimal projectRate,
                                                   BigDecimal roleRatio,
                                                   BigDecimal completionRatio) {
        return money(stageBenefitDelta)
                .multiply(defaultValue(projectRate))
                .multiply(defaultValue(roleRatio))
                .multiply(completionRatio == null ? new BigDecimal("100") : completionRatio)
                .divide(new BigDecimal("1000000"), 2, RoundingMode.HALF_UP);
    }

    public static Result allocate(List<PendingAdjustment> pendingAdjustments,
                                  Map<Long, BigDecimal> normalAmountsByUser) {
        Map<Long, BigDecimal> availableByUser = new LinkedHashMap<>();
        if (normalAmountsByUser != null) {
            normalAmountsByUser.forEach((userId, amount) ->
                    availableByUser.put(userId, money(amount).max(BigDecimal.ZERO)));
        }

        List<PendingAdjustment> ordered = pendingAdjustments == null
                ? List.of()
                : pendingAdjustments.stream()
                .filter(item -> item != null && item.userId() != null)
                .toList();
        List<AppliedAdjustment> applied = new ArrayList<>();

        // Credits are available to absorb a debit in the same settlement batch.
        ordered.stream().filter(item -> item.pendingAmount().signum() > 0)
                .forEach(item -> apply(item, availableByUser, applied));
        ordered.stream().filter(item -> item.pendingAmount().signum() < 0)
                .forEach(item -> apply(item, availableByUser, applied));

        BigDecimal appliedTotal = applied.stream()
                .map(AppliedAdjustment::appliedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingTotal = applied.stream()
                .map(AppliedAdjustment::remainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Result(List.copyOf(applied), money(appliedTotal), money(remainingTotal));
    }

    private static void apply(PendingAdjustment item,
                              Map<Long, BigDecimal> availableByUser,
                              List<AppliedAdjustment> result) {
        BigDecimal pending = money(item.pendingAmount());
        BigDecimal applied;
        if (pending.signum() > 0) {
            applied = pending;
        } else {
            BigDecimal available = availableByUser.getOrDefault(item.userId(), money(BigDecimal.ZERO));
            applied = pending.max(available.negate());
        }
        BigDecimal remaining = pending.subtract(applied).setScale(2, RoundingMode.HALF_UP);
        availableByUser.merge(item.userId(), applied, BigDecimal::add);
        result.add(new AppliedAdjustment(item, applied, remaining));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal defaultValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record PendingAdjustment(
            Long sourceDistributionId,
            Long sourceOutputValueId,
            Long sourceProjectStageId,
            String sourceStageName,
            Long userId,
            Integer workType,
            BigDecimal targetAmount,
            BigDecimal previousAdjustedAmount,
            BigDecimal pendingAmount
    ) {
    }

    public record AppliedAdjustment(
            PendingAdjustment source,
            BigDecimal appliedAmount,
            BigDecimal remainingAmount
    ) {
    }

    public record Result(
            List<AppliedAdjustment> adjustments,
            BigDecimal appliedTotal,
            BigDecimal remainingTotal
    ) {
    }
}
