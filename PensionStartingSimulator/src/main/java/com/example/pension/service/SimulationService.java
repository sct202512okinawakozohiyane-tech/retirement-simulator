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

        BigDecimal balance = BigDecimal.valueOf(request.dcBalance);
        List<YearResult> results = new ArrayList<>();

        BigDecimal inflationMultiplier = BigDecimal.ONE;
        BigDecimal pensionMultiplier = BigDecimal.ONE;
        Integer depletionAge = null;

        for (int age = request.startAge; age <= request.lifeExpectancy; age++) {
            // 1年分のステップ計算 (決定論的: randomReturn=0)
            SimulationStepResult step = runYearlyStep(request, age, balance, inflationMultiplier, pensionMultiplier, BigDecimal.ZERO);
            
            YearResult year = new YearResult();
            year.setAge(age);
            year.setIncome(step.income.setScale(0, RoundingMode.HALF_UP).doubleValue());
            year.setExpense(step.expense.setScale(0, RoundingMode.HALF_UP).doubleValue());
            year.setBalance(step.endBalance.doubleValue());
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
        
        // 支出計算
        BigDecimal yearlyExpense = BigDecimal.valueOf(request.basicLivingCost + request.leisureCost)
                .multiply(BigDecimal.valueOf(12))
                .multiply(inflationMultiplier);

        BigDecimal income = BigDecimal.ZERO;

        // 給与収入
        if (age < request.retirementAge) {
            income = income.add(BigDecimal.valueOf(request.salaryAfter60).multiply(BigDecimal.valueOf(12)).multiply(SALARY_NET_RATE));
        }

        // 年金収入
        if (age >= request.pensionStartAge) {
            income = income.add(BigDecimal.valueOf(request.publicPension).multiply(BigDecimal.valueOf(12)).multiply(pensionMultiplier).multiply(PENSION_NET_RATE));
        }

        BigDecimal shortfall = yearlyExpense.subtract(income);
        BigDecimal currentBalance = startBalance;

        // 不足分をDC残高から取り崩し
        if (shortfall.compareTo(BigDecimal.ZERO) > 0 && currentBalance.compareTo(BigDecimal.ZERO) > 0 && age >= request.pensionStartAge) {
            // 取り崩し額（額面）の計算
            BigDecimal withdrawalNeeded = shortfall.divide(PENSION_NET_RATE, 10, RoundingMode.HALF_UP);
            BigDecimal withdrawal = currentBalance.min(withdrawalNeeded);
            currentBalance = currentBalance.subtract(withdrawal);
            income = income.add(withdrawal.multiply(PENSION_NET_RATE));
        }

        // 年末残高計算（利回りの適用）
        BigDecimal totalReturnRate = BigDecimal.valueOf(request.dcReturnRate).add(randomReturn);
        BigDecimal endBalance = currentBalance.multiply(BigDecimal.ONE.add(totalReturnRate));
        if (endBalance.compareTo(BigDecimal.ZERO) < 0) endBalance = BigDecimal.ZERO;

        // 次の年のマルチプライヤー準備
        BigDecimal nextInflationMultiplier = inflationMultiplier.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(request.inflationRate)));
        BigDecimal nextPensionMultiplier = pensionMultiplier;
        if (age >= request.pensionStartAge) {
            // マクロ経済スライド等を想定した0.8倍の調整
            BigDecimal pensionAdjustment = BigDecimal.valueOf(request.inflationRate).multiply(new BigDecimal("0.8"));
            nextPensionMultiplier = pensionMultiplier.multiply(BigDecimal.ONE.add(pensionAdjustment));
        }

        return new SimulationStepResult(income, yearlyExpense, endBalance, nextInflationMultiplier, nextPensionMultiplier);
    }

    private double calculateFailureProbability(SimulationRequest request) {
        int simulations = 1000;
        int failureCount = 0;

        for (int i = 0; i < simulations; i++) {
            BigDecimal balance = BigDecimal.valueOf(request.dcBalance);
            BigDecimal inflationMultiplier = BigDecimal.ONE;
            BigDecimal pensionMultiplier = BigDecimal.ONE;

            for (int age = request.startAge; age <= request.lifeExpectancy; age++) {
                BigDecimal randomReturn = BigDecimal.valueOf(request.returnVolatility * ThreadLocalRandom.current().nextGaussian());
                
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
        if (request.getStartAge() < 0 || request.getLifeExpectancy() <= 0 || request.getStartAge() >= request.getLifeExpectancy()) {
            throw new IllegalArgumentException("Invalid age parameters");
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
