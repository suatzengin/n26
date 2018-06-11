package com.n26.interview.analytics.controller;

class StatisticsResponse {

    StatisticsResponse(double sum, double avg, double max, double min, int count) {
        this.sum = sum;
        this.average = avg;
        this.max = max;
        this.min = min;
        this.count = count;
    }

    private double sum;
    private double max;
    private double min;
    private double average;
    private int count;

    public double getSum() {
        return sum;
    }

    public double getMax() {
        return max;
    }

    public double getMin() {
        return min;
    }

    public double getAverage() {
        return average;
    }

    public int getCount() {
        return count;
    }
}
