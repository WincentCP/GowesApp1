package edu.uph.m23si1.gowesapp;

public class Transaction {
    private double amount;
    private String description;
    private long timestamp;
    private String type; // "TopUp", "RidePayment", "Refund", "Hold"
    private String status;

    public Transaction() {} // Required for Firestore

    public Transaction(double amount, String description, long timestamp, String type, String status) {
        this.amount = amount;
        this.description = description;
        this.timestamp = timestamp;
        this.type = type;
        this.status = status;
    }

    public double getAmount() { return amount; }
    public String getDescription() { return description; }
    public long getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getStatus() { return status; }
}