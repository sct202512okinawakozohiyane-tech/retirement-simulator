package com.example.pension.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

import com.example.pension.model.SimulationRequest;
import com.example.pension.model.SimulationResponse;
import com.example.pension.model.YearResult;

@Service
public class SimulationService {

    private static final double SALARY_NET_RATE = 0.8;
    private static final double PENSION_NET_RATE = 0.9;

    public SimulationResponse simulate(SimulationRequest request) {
        validateRequest(request);

        double balance = request.dcBalance;
        List<YearResult> results = new ArrayList<>();

        double inflationMultiplier = 1.0;
        double pensionMultiplier = 1.0;
        Integer depletionAge = null;

        for (int age = request.startAge; age <= request.lifeExpectancy; age++) {

            YearResult year = simulateYear(
                request, age, balance, inflationMultiplier, pensionMultiplier);

            balance = calculateYearEndBalance(request, year.balance, 0);
            if (balance == 0 && depletionAge == null) {
                depletionAge = age;
            }

            results.add(year);

            inflationMultiplier *= (1 + request.inflationRate);

            if (age >= request.pensionStartAge) {
                pensionMultiplier *= (1 + request.inflationRate * 0.8);
            }
        }

        SimulationResponse response = new SimulationResponse();
        response.remainingBalanceAt100 = balance;
        response.yearlyResults = results;
        response.assetDepletionAge =
            (depletionAge == null) ? -1 : depletionAge;

        response.failureProbability =
            calculateFailureProbability(request);

        return response;
    }

    private YearResult simulateYear(SimulationRequest request, int age,
            double balance, double inflationMultiplier, double pensionMultiplier) {

        double income = 0;

        double yearlyExpense =
            (request.basicLivingCost + request.leisureCost)
            * 12
            * inflationMultiplier;

        if (age < request.retirementAge) {
            double salaryNet =
                request.salaryAfter60 * 12 * SALARY_NET_RATE;
            income += salaryNet;
        }

        if (age >= request.pensionStartAge) {

            double pensionNet =
                request.publicPension
                * 12
                * pensionMultiplier
                * PENSION_NET_RATE;

            income += pensionNet;
        }

        double shortfall = yearlyExpense - income;

        if (shortfall > 0 && balance > 0 && age >= request.pensionStartAge) {

            double withdrawalNeeded =
                shortfall / PENSION_NET_RATE;

            double withdrawal =
                Math.min(withdrawalNeeded, balance);

            balance -= withdrawal;

            income += withdrawal * PENSION_NET_RATE;
        }

        if (balance < 0) {
            balance = 0;
        }

        YearResult year = new YearResult();
        year.age = age;
        year.income = Math.round(income);
        year.expense = Math.round(yearlyExpense);
        year.balance = balance;

        return year;
    }

    private double calculateYearEndBalance(SimulationRequest request,
            double balance, double returnRate) {
        return balance * (1 + request.dcReturnRate + returnRate);
    }

    private void validateRequest(SimulationRequest request) {
        if (request.startAge < 0 || request.lifeExpectancy <= 0) {
            throw new IllegalArgumentException("Invalid age parameters");
        }
        if (request.startAge >= request.lifeExpectancy) {
            throw new IllegalArgumentException("startAge must be less than lifeExpectancy");
        }
        if (request.retirementAge < 0 || request.pensionStartAge < 0) {
            throw new IllegalArgumentException("Invalid retirement or pension age");
        }
        if (request.inflationRate < 0 || request.dcReturnRate < -1) {
            throw new IllegalArgumentException("Invalid rate parameters");
        }
        if (request.dcBalance < 0 || request.basicLivingCost < 0 || request.leisureCost < 0) {
            throw new IllegalArgumentException("Invalid cost parameters");
        }
    }

    private double[] simulateSingleYear(SimulationRequest request, int age,
            double balance, double inflationMultiplier, double pensionMultiplier,
            double returnRate) {

        YearResult year = simulateYear(
            request, age, balance, inflationMultiplier, pensionMultiplier);

        balance = calculateYearEndBalance(request, year.balance, returnRate);

        if (balance <= 0) {
            balance = 0;
        }

        inflationMultiplier *= (1 + request.inflationRate);

        if (age >= request.pensionStartAge) {
            pensionMultiplier *= (1 + request.inflationRate * 0.8);
        }

        return new double[] {balance, inflationMultiplier, pensionMultiplier};
    }

    private double calculateFailureProbability(SimulationRequest request) {

        int simulations = 1000;
        int failureCount = 0;

        for (int i = 0; i < simulations; i++) {

            double balance = request.dcBalance;

            double inflationMultiplier = 1.0;
            double pensionMultiplier = 1.0;

            for (int age = request.startAge; age <= request.lifeExpectancy; age++) {

                double randomReturn =
                    request.returnVolatility * ThreadLocalRandom.current().nextGaussian();

                double[] result = simulateSingleYear(request, age, balance,
                    inflationMultiplier, pensionMultiplier, randomReturn);
                balance = result[0];
                inflationMultiplier = result[1];
                pensionMultiplier = result[2];

                if (balance <= 0) {
                    failureCount++;
                    break;
                }
            }
        }

        return (double) failureCount / simulations;
    }
}
