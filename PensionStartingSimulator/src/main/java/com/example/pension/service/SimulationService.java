package com.example.pension.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

            // 年初運用
            balance = balance * (1 + request.dcReturnRate);

            double income = 0;

            double yearlyExpense =
                (request.basicLivingCost + request.leisureCost)
                * 12
                * inflationMultiplier;

            // 給与
            if (age < request.retirementAge) {
                double salaryNet =
                    request.salaryAfter60 * 12 * SALARY_NET_RATE;
                income += salaryNet;
            }

            // 年金
            if (age >= request.pensionStartAge) {

                double pensionNet =
                    request.publicPension
                    * 12
                    * pensionMultiplier
                    * PENSION_NET_RATE;

                income += pensionNet;
            }

            double shortfall = yearlyExpense - income;

            if (shortfall > 0 && balance > 0) {

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

            if (balance == 0 && depletionAge == null) {
                depletionAge = age;
            }

            YearResult year = new YearResult();
            year.age = age;
            year.income = Math.round(income);
            year.expense = Math.round(yearlyExpense);
            year.balance = Math.round(balance);

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

        // ★モンテカルロ計算
        response.failureProbability =
            calculateFailureProbability(request);

        return response;
    }

    // ★モンテカルロ
    private double calculateFailureProbability(SimulationRequest req) {

        int simulations = 1000;
        int failureCount = 0;

        Random rand = new Random();

        for (int i = 0; i < simulations; i++) {

            double balance = req.dcBalance;

            double inflationMultiplier = 1.0;
            double pensionMultiplier = 1.0;

            for (int age = req.startAge; age <= req.lifeExpectancy; age++) {

                // ランダム利回り
                double randomReturn =
                    req.dcReturnRate +
                    req.returnVolatility * rand.nextGaussian();

                balance = balance * (1 + randomReturn);

                double income = 0;

                double yearlyExpense =
                    (req.basicLivingCost + req.leisureCost)
                    * 12
                    * inflationMultiplier;

                // 給与
                if (age < req.retirementAge) {
                    income += req.salaryAfter60 * 12 * SALARY_NET_RATE;
                }

                // 年金
                if (age >= req.pensionStartAge) {
                    income +=
                        req.publicPension
                        * 12
                        * pensionMultiplier
                        * PENSION_NET_RATE;
                }

                double shortfall = yearlyExpense - income;

                if (shortfall > 0) {

                    double withdrawalNeeded =
                        shortfall / PENSION_NET_RATE;

                    balance -= withdrawalNeeded;
                }

                if (balance <= 0) {
                    failureCount++;
                    break;
                }

                inflationMultiplier *= (1 + req.inflationRate);

                if (age >= req.pensionStartAge) {
                    pensionMultiplier *=
                        (1 + req.inflationRate * 0.8);
                }
            }
        }

        return (double) failureCount / simulations;
    }
}