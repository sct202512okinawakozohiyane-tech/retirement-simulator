package com.example.pension.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import com.example.pension.model.SimulationRequest;
import com.example.pension.model.SimulationResponse;
import com.example.pension.model.YearResult;

@Service
public class SimulationService {

    private static final BigDecimal SALARY_NET_RATE = new BigDecimal("0.8");
    private static final BigDecimal PENSION_NET_RATE = new BigDecimal("0.9");

    public SimulationResponse simulate(SimulationRequest request) {
        validateRequest(request);

        BigDecimal balance = BigDecimal.valueOf(request.getDcBalance());
        List<YearResult> results = new ArrayList<>();

        BigDecimal inflationMultiplier = BigDecimal.ONE;
        BigDecimal pensionMultiplier = BigDecimal.ONE;
        Integer depletionAge = null;

        for (int age = request.getStartAge(); age <= request.getLifeExpectancy(); age++) {
            // ★ 利回りを先适用、その後で取り崩し計算
            SimulationStepResult step = runYearlyStep(request, age, balance, inflationMultiplier, pensionMultiplier, BigDecimal.ZERO);
            
            YearResult year = new YearResult();
            year.setAge(age);
            year.setIncome(step.income.setScale(0, RoundingMode.HALF_UP).doubleValue());
            year.setExpense(step.expense.setScale(0, RoundingMode.HALF_UP).doubleValue());
            year.setBalance(step.endBalance.setScale(0, RoundingMode.HALF_UP).doubleValue());

            results.add(year);

            balance = step.endBalance;
            inflationMultiplier = step.nextInflationMultiplier;
            pensionMultiplier = step.nextPensionMultiplier;

            if (balance.compareTo(BigDecimal.ZERO) <= 0 && depletionAge == null) {
                depletionAge = age;
            }
        }

        SimulationResponse response = new SimulationResponse();
        response.setRemainingBalanceAt100(balance.doubleValue());
        response.setYearlyResults(results);
        response.setAssetDepletionAge((depletionAge == null) ? -1 : depletionAge);
        response.setFailureProbability(calculateFailureProbability(request));

        return response;
    }

    private SimulationStepResult runYearlyStep(SimulationRequest request, int age, BigDecimal startBalance, 
                                              BigDecimal inflationMultiplier, BigDecimal pensionMultiplier, BigDecimal randomReturn) {
        
        // ★ 利回りを先に適用（年度始め残高にリターンを乗せる）
        BigDecimal totalReturnRate = BigDecimal.valueOf(request.getDcReturnRate()).add(randomReturn);
        BigDecimal balanceAfterReturn = startBalance.multiply(BigDecimal.ONE.add(totalReturnRate));
        if (balanceAfterReturn.compareTo(BigDecimal.ZERO) < 0) {
            balanceAfterReturn = BigDecimal.ZERO;
        }

        // 支出計算
        BigDecimal yearlyExpense = BigDecimal.valueOf(request.getBasicLivingCost() + request.getLeisureCost())
                .multiply(BigDecimal.valueOf(12))
                .multiply(inflationMultiplier);

        BigDecimal income = BigDecimal.ZERO;

        // 給与収入
        if (age < request.getRetirementAge()) {
            income = income.add(BigDecimal.valueOf(request.getSalaryAfter60()).multiply(BigDecimal.valueOf(12)).multiply(SALARY_NET_RATE));
        }

        // 年金収入
        if (age >= request.getPensionStartAge()) {
            income = income.add(BigDecimal.valueOf(request.getPublicPension()).multiply(BigDecimal.valueOf(12)).multiply(pensionMultiplier).multiply(PENSION_NET_RATE));
        }

        BigDecimal shortfall = yearlyExpense.subtract(income);
        BigDecimal currentBalance = balanceAfterReturn;

        // 不足分をDC残高から取り崩し
        if (shortfall.compareTo(BigDecimal.ZERO) > 0 && currentBalance.compareTo(BigDecimal.ZERO) > 0 && age >= request.getPensionStartAge()) {
            BigDecimal withdrawalNeeded = shortfall.divide(PENSION_NET_RATE, 10, RoundingMode.HALF_UP);
            BigDecimal withdrawal = currentBalance.min(withdrawalNeeded);
            currentBalance = currentBalance.subtract(withdrawal);
            income = income.add(withdrawal.multiply(PENSION_NET_RATE));
        }

        BigDecimal endBalance = currentBalance;
        if (endBalance.compareTo(BigDecimal.ZERO) < 0) {
            endBalance = BigDecimal.ZERO;
        }

        // 次の年のマルチプライヤー準備
        BigDecimal nextInflationMultiplier = inflationMultiplier.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(request.getInflationRate())));
        BigDecimal nextPensionMultiplier = pensionMultiplier;
        if (age >= request.getPensionStartAge()) {
            BigDecimal pensionAdjustment = BigDecimal.valueOf(request.getInflationRate()).multiply(new BigDecimal("0.8"));
            nextPensionMultiplier = pensionMultiplier.multiply(BigDecimal.ONE.add(pensionAdjustment));
        }

        return new SimulationStepResult(income, yearlyExpense, endBalance, nextInflationMultiplier, nextPensionMultiplier);
    }

    private double calculateFailureProbability(SimulationRequest request) {
        int simulations = 1000;
        int failureCount = 0;

        for (int i = 0; i < simulations; i++) {
            BigDecimal balance = BigDecimal.valueOf(request.getDcBalance());
            BigDecimal inflationMultiplier = BigDecimal.ONE;
            BigDecimal pensionMultiplier = BigDecimal.ONE;

            for (int age = request.getStartAge(); age <= request.getLifeExpectancy(); age++) {
                BigDecimal randomReturn = BigDecimal.valueOf(request.getReturnVolatility() * ThreadLocalRandom.current().nextGaussian());
                
                SimulationStepResult result = runYearlyStep(request, age, balance, inflationMultiplier, pensionMultiplier, randomReturn);
                balance = result.endBalance;
                inflationMultiplier = result.nextInflationMultiplier;
                pensionMultiplier = result.nextPensionMultiplier;

                if (balance.compareTo(BigDecimal.ZERO) <= 0) {
                    failureCount++;
                    break;
                }
            }
        }
        return (double) failureCount / simulations;
    }

    private void validateRequest(SimulationRequest request) {
        if (request.getStartAge() < 0 || request.getLifeExpectancy() <= 0) {
            throw new IllegalArgumentException("Invalid age parameters");
        }
        if (request.getStartAge() >= request.getLifeExpectancy()) {
            throw new IllegalArgumentException("startAge must be less than lifeExpectancy");
        }
        if (request.getRetirementAge() < 0 || request.getPensionStartAge() < 0) {
            throw new IllegalArgumentException("Invalid retirement or pension age");
        }
        if (request.getInflationRate() < 0 || request.getDcReturnRate() < -1) {
            throw new IllegalArgumentException("Invalid rate parameters");
        }
        if (request.getDcBalance() < 0 || request.getBasicLivingCost() < 0 || request.getLeisureCost() < 0) {
            throw new IllegalArgumentException("Invalid cost parameters");
        }
    }

    private static class SimulationStepResult {
        final BigDecimal income;
        final BigDecimal expense;
        final BigDecimal endBalance;
        final BigDecimal nextInflationMultiplier;
        final BigDecimal nextPensionMultiplier;

        SimulationStepResult(BigDecimal income, BigDecimal expense, BigDecimal endBalance, BigDecimal nextInflationMultiplier, BigDecimal nextPensionMultiplier) {
            this.income = income;
            this.expense = expense;
            this.endBalance = endBalance;
            this.nextInflationMultiplier = nextInflationMultiplier;
            this.nextPensionMultiplier = nextPensionMultiplier;
        }
    }
}
