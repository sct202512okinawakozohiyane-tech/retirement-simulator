package com.example.pension.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.pension.model.SimulationRequest;
import com.example.pension.model.SimulationResponse;
import com.example.pension.model.YearResult;

@Service
public class SimulationService {
	// 手取り率（税・社会保険の簡易モデル）
    private static final double SALARY_NET_RATE = 0.8;
    private static final double PENSION_NET_RATE = 0.9;

    public SimulationResponse simulate(SimulationRequest request) {

        double balance = request.dcBalance;   // DC残高
        List<YearResult> results = new ArrayList<>();

        double inflationMultiplier = 1.0;
        double pensionMultiplier = 1.0;
        Integer depletionAge = null;
        
        for (int age = request.startAge; age <= request.lifeExpectancy; age++) {
                 	
        	//年初に運用
            balance = balance * (1 + request.dcReturnRate);

            double income = 0;

            // インフレ考慮した年間支出
            double yearlyExpense =
                (request.basicLivingCost + request.leisureCost)
                * 12
                * inflationMultiplier;

         // 収入判定（併給可）
            income = 0;

            // 働いているなら給与
            if (age < request.retirementAge) {
                double salaryNet = request.salaryAfter60 * 12 * SALARY_NET_RATE;
                income += salaryNet;
            }

            // 年金開始後なら年金
            if (age >= request.pensionStartAge) {
            	double pensionNet =
            		    request.publicPension * 12 * pensionMultiplier * PENSION_NET_RATE;

            		income += pensionNet;
            }

         // 不足額
            double shortfall = yearlyExpense - income;

            if (shortfall > 0 && balance > 0) {

                // 不足分だけ取り崩す
            	double withdrawalNeeded = shortfall / PENSION_NET_RATE;

            	double withdrawal = Math.min(withdrawalNeeded, balance);

            	balance -= withdrawal;

            	income += withdrawal * PENSION_NET_RATE; // 取り崩しも税・社会保険の対象とする場合;
            }

            // 念のためゼロ未満防止
            if (balance < 0) {
                balance = 0;
            }
            
            // 資産が尽きた年を記録
            if (balance == 0 && depletionAge == null) {
                depletionAge = age;
            }
            
            // 結果格納
            YearResult year = new YearResult();
            year.age = age;
            year.income = Math.round(income);
            year.expense = Math.round(yearlyExpense);
            year.balance = Math.round(balance);
            
            results.add(year);

         // インフレ更新
            inflationMultiplier *= (1 + request.inflationRate);

            // 年金開始後のみスライド更新
            if (age >= request.pensionStartAge) {
                pensionMultiplier *= (1 + request.inflationRate * 0.8);
            }
        }
        
        SimulationResponse response = new SimulationResponse();
        response.remainingBalanceAt100 = balance;
        response.yearlyResults = results;
        response.assetDepletionAge = (depletionAge == null) ? -1 : depletionAge;

        return response;
    }
}