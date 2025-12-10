package edu.uph.m23si1.gowesapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RideCompleteActivity extends AppCompatActivity {

    private TextView tvSubtitle, tvBikeName;
    private TextView tvRideDuration, tvAmountPaid, tvRideCharge, tvPaymentMethod, tvCo2Saved;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    public static final String EXTRA_RIDE_DURATION_MS   = "EXTRA_RIDE_DURATION_MS";
    public static final String EXTRA_BASE_CHARGE        = "EXTRA_BASE_CHARGE";
    public static final String EXTRA_PAYMENT_METHOD     = "EXTRA_PAYMENT_METHOD";
    public static final String EXTRA_CO2_SAVED          = "EXTRA_CO2_SAVED";
    public static final String EXTRA_BIKE_ID            = "EXTRA_BIKE_ID";

    private static final int DEFAULT_BASE_CHARGE = 8000;
    private boolean isTransactionProcessed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ride_complete);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        tvSubtitle      = findViewById(R.id.tv_subtitle);
        tvBikeName      = findViewById(R.id.tv_bike_name);
        tvRideDuration  = findViewById(R.id.tv_ride_duration);
        tvAmountPaid    = findViewById(R.id.tv_amount_paid);
        tvRideCharge    = findViewById(R.id.tv_ride_charge);
        tvPaymentMethod = findViewById(R.id.tv_payment_method);
        tvCo2Saved      = findViewById(R.id.tv_co2_saved);
        Button btnBackHome = findViewById(R.id.btn_back_home);

        fetchUserName();

        if (savedInstanceState == null) {
            populateRideDataAndProcessTransaction();
        }

        if (btnBackHome != null) {
            btnBackHome.setOnClickListener(v -> {
                Intent intent = new Intent(RideCompleteActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        }
    }

    private void fetchUserName() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(snap -> {
                        if (snap.exists()) {
                            String name = snap.getString("fullName");
                            if (name != null) tvSubtitle.setText("Thank you, " + name.split(" ")[0] + "! Stay Safe");
                        }
                    });
        }
    }

    private void populateRideDataAndProcessTransaction() {
        Intent intent = getIntent();

        long durationMs = intent.getLongExtra(EXTRA_RIDE_DURATION_MS, 0L);
        int baseCharge = intent.getIntExtra(EXTRA_BASE_CHARGE, DEFAULT_BASE_CHARGE);
        String paymentMethod = intent.getStringExtra(EXTRA_PAYMENT_METHOD);
        if (paymentMethod == null) paymentMethod = "Wallet";
        String bikeId = intent.getStringExtra(EXTRA_BIKE_ID);
        double co2Grams = intent.getDoubleExtra(EXTRA_CO2_SAVED, 0.0);

        // Discount Logic
        double discountRate = paymentMethod.equalsIgnoreCase("Wallet") ? 0.05 : 0.0;
        int discountAmount = (int) Math.round(baseCharge * discountRate);
        int finalCharge = baseCharge - discountAmount;

        // UI Updates
        if (tvRideDuration != null) tvRideDuration.setText(formatDuration(durationMs));
        if (tvBikeName != null) tvBikeName.setText(bikeId != null ? bikeId : "Bike Rental");
        if (tvRideCharge != null) tvRideCharge.setText(formatCurrency(baseCharge));
        if (tvAmountPaid != null) tvAmountPaid.setText(formatCurrency(finalCharge));
        if (tvPaymentMethod != null) tvPaymentMethod.setText(discountRate > 0 ? paymentMethod + " (-5%)" : paymentMethod);
        if (tvCo2Saved != null) tvCo2Saved.setText(String.format(Locale.getDefault(), "%.0fg", co2Grams));

        // PROCESS TRANSACTION (Deduct & Save Stats)
        if (!isTransactionProcessed && paymentMethod.equalsIgnoreCase("Wallet")) {
            processPaymentAndStats(finalCharge, bikeId, co2Grams);
            isTransactionProcessed = true;
        }
    }

    private void processPaymentAndStats(int amount, String bikeId, double co2Grams) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        DocumentReference userRef = db.collection("users").document(user.getUid());

        // 1. Deduct Balance
        userRef.update("walletBalance", FieldValue.increment(-amount));

        // 2. Update Stats (Total Rides & CO2)
        // We use dot notation to update nested fields.
        // Important: "stats" map must exist (HomeFragment seeder ensures this now)
        double co2Kg = co2Grams / 1000.0;
        userRef.update(
                "stats.totalRides", FieldValue.increment(1),
                "stats.totalCO2Saved", FieldValue.increment(co2Kg)
        ).addOnFailureListener(e -> {
            // Fallback: If stats field missing, creating it entirely
            Map<String, Object> newStats = new HashMap<>();
            newStats.put("totalRides", 1);
            newStats.put("totalCO2Saved", co2Kg);
            userRef.set(Map.of("stats", newStats), SetOptions.merge());
        });

        // 3. Save Transaction History
        Map<String, Object> txn = new HashMap<>();
        txn.put("amount", -amount);
        txn.put("description", "Ride Payment - " + (bikeId != null ? bikeId : "Bike"));
        txn.put("timestamp", System.currentTimeMillis());
        txn.put("type", "RidePayment");
        txn.put("status", "Success");

        userRef.collection("transactions").add(txn);
    }

    private String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String formatCurrency(int amount) {
        Locale localeID = new Locale("in", "ID");
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(localeID);
        currencyFormatter.setMaximumFractionDigits(0);
        return currencyFormatter.format(amount);
    }
}