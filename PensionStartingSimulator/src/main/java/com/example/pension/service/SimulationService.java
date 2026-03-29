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

        double balance = request.dcBalance;
        List<YearResult> results = new ArrayList<>();

        double inflationMultiplier = 1.0;
        double pensionMultiplier = 1.0;
        Integer depletionAge = null;

        for (int age = request.startAge; age <= request.lifeExpectancy; age++) {

            balance = calculateYearEndBalance(request, balance, 0);

            YearResult year = simulateYear(
                request, age, balance, inflationMultiplier, pensionMultiplier);

            balance = year.balance;
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

        if (shortfall > 0 && balance > 0 && age >= 60) {

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
        year.balance = Math.round(balance);

        return year;
    }

    private double calculateYearEndBalance(SimulationRequest request,
            double balance, double returnRate) {
        return balance * (1 + request.dcReturnRate + returnRate);
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

                balance = calculateYearEndBalance(request, balance, randomReturn);

                YearResult year = simulateYear(
                    request, age, balance, inflationMultiplier, pensionMultiplier);

                balance = year.balance;

                if (balance <= 0) {
                    failureCount++;
                    break;
                }

                inflationMultiplier *= (1 + request.inflationRate);

                if (age >= request.pensionStartAge) {
                    pensionMultiplier *=
                        (1 + request.inflationRate * 0.8);
                }
            }
        }

        return (double) failureCount / simulations;
    }
}
