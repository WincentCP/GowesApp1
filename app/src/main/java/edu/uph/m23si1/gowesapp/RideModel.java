package edu.uph.m23si1.gowesapp;

public class RideModel {
    private String duration;
    private long durationSeconds;
    private int baseCost;
    private int finalCost;
    private String paymentMethod;
    private double co2SavedGrams;
    private long timestamp;
    private String bikeId;

    // Required empty constructor for Firestore
    public RideModel() {}

    public RideModel(String duration, int finalCost, long timestamp) {
        this.duration = duration;
        this.finalCost = finalCost;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getBaseCost() {
        return baseCost;
    }

    public void setBaseCost(int baseCost) {
        this.baseCost = baseCost;
    }

    public int getFinalCost() {
        return finalCost;
    }

    public void setFinalCost(int finalCost) {
        this.finalCost = finalCost;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public double getCo2SavedGrams() {
        return co2SavedGrams;
    }

    public void setCo2SavedGrams(double co2SavedGrams) {
        this.co2SavedGrams = co2SavedGrams;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getBikeId() {
        return bikeId != null ? bikeId : "Bike Ride";
    }

    public void setBikeId(String bikeId) {
        this.bikeId = bikeId;
    }
}