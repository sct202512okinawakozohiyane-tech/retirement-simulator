package com.example.pension.model;

import java.util.List;

public class SimulationResponse {

    private double remainingBalanceAt100;
    private int assetDepletionAge;
    private List<YearResult> yearlyResults;
    private double failureProbability;

    public double getRemainingBalanceAt100() { return remainingBalanceAt100; }
    public void setRemainingBalanceAt100(double remainingBalanceAt100) { this.remainingBalanceAt100 = remainingBalanceAt100; }

    public int getAssetDepletionAge() { return assetDepletionAge; }
    public void setAssetDepletionAge(int assetDepletionAge) { this.assetDepletionAge = assetDepletionAge; }

    public List<YearResult> getYearlyResults() { return yearlyResults; }
    public void setYearlyResults(List<YearResult> yearlyResults) { this.yearlyResults = yearlyResults; }

    public double getFailureProbability() { return failureProbability; }
    public void setFailureProbability(double failureProbability) { this.failureProbability = failureProbability; }
}
