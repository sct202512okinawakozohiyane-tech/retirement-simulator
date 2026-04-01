package com.example.pension.model;

public class SimulationRequest {

	private int startAge;
	private int lifeExpectancy;
	private int retirementAge;
    private int pensionStartAge;

    private double basicLivingCost;
    private double leisureCost;

    private double salaryAfter60;
    private double publicPension;

    private double dcBalance;
    private double dcReturnRate;
       
    private double inflationRate;
    private double returnVolatility;

    // Getters and Setters
    public int getStartAge() { return startAge; }
    public void setStartAge(int startAge) { this.startAge = startAge; }

    public int getLifeExpectancy() { return lifeExpectancy; }
    public void setLifeExpectancy(int lifeExpectancy) { this.lifeExpectancy = lifeExpectancy; }

    public int getRetirementAge() { return retirementAge; }
    public void setRetirementAge(int retirementAge) { this.retirementAge = retirementAge; }

    public int getPensionStartAge() { return pensionStartAge; }
    public void setPensionStartAge(int pensionStartAge) { this.pensionStartAge = pensionStartAge; }

    public double getBasicLivingCost() { return basicLivingCost; }
    public void setBasicLivingCost(double basicLivingCost) { this.basicLivingCost = basicLivingCost; }

    public double getLeisureCost() { return leisureCost; }
    public void setLeisureCost(double leisureCost) { this.leisureCost = leisureCost; }

    public double getSalaryAfter60() { return salaryAfter60; }
    public void setSalaryAfter60(double salaryAfter60) { this.salaryAfter60 = salaryAfter60; }

    public double getPublicPension() { return publicPension; }
    public void setPublicPension(double publicPension) { this.publicPension = publicPension; }

    public double getDcBalance() { return dcBalance; }
    public void setDcBalance(double dcBalance) { this.dcBalance = dcBalance; }

    public double getDcReturnRate() { return dcReturnRate; }
    public void setDcReturnRate(double dcReturnRate) { this.dcReturnRate = dcReturnRate; }

    public double getInflationRate() { return inflationRate; }
    public void setInflationRate(double inflationRate) { this.inflationRate = inflationRate; }

    public double getReturnVolatility() { return returnVolatility; }
    public void setReturnVolatility(double returnVolatility) { this.returnVolatility = returnVolatility; }
}
