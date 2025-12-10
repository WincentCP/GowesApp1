package edu.uph.m23si1.gowesapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ActiveRideActivity extends AppCompatActivity {

    private static final String TAG = "ActiveRideActivity";
    private static final double RATE_PER_BLOCK = 8000.0;
    private static final double SECONDS_PER_BLOCK = 3600.0;

    private TextView tvTimer, tvCurrentCost, tvBikeName;
    private Handler timerHandler;
    private Runnable timerRunnable;

    private long startTime = 0;
    private long timeInMilliseconds = 0;
    private String bikeModel = "Gowes Bike";
    private String bikeId = "BK-UNKNOWN";

    private double finalCalculatedCost = 0.0;
    private String finalRideDuration;

    private FirebaseFirestore db;
    private DatabaseReference rtDbRef;
    private String userId;
    private String paymentMethod = "Wallet";
    private String currentRideDocId; // To hold "ride_123456"

    private final ActivityResultLauncher<Intent> cameraResultLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            Toast.makeText(this, "Photo captured!", Toast.LENGTH_SHORT).show();
                            endRide();
                        } else {
                            Toast.makeText(this, "Photo cancelled.", Toast.LENGTH_SHORT).show();
                            resumeTimerAfterCancel();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_ride);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        userId = user.getUid();

        tvTimer = findViewById(R.id.tv_timer);
        tvCurrentCost = findViewById(R.id.tv_current_cost);
        tvBikeName = findViewById(R.id.tv_bike_name);
        Button parkButton = findViewById(R.id.btn_park);
        Button backHomeButton = findViewById(R.id.btn_back_home);

        boolean isNewRide = getIntent().getBooleanExtra("IS_NEW_RIDE", false);

        if (isNewRide) {
            initializeNewRide();
        } else {
            resumeRideState();
        }

        parkButton.setOnClickListener(v -> {
            timerHandler.removeCallbacks(timerRunnable);
            finalRideDuration = tvTimer.getText().toString();
            showDetectingDialog();
        });

        backHomeButton.setOnClickListener(v -> {
            Intent intent = new Intent(ActiveRideActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
    }

    private void initializeNewRide() {
        // 1. Get Data
        paymentMethod = getIntent().getStringExtra(RideCompleteActivity.EXTRA_PAYMENT_METHOD);
        if (paymentMethod == null) paymentMethod = "Wallet";

        String intentModel = getIntent().getStringExtra("BIKE_MODEL");
        if (intentModel != null) bikeModel = intentModel;

        bikeId = getIntent().getStringExtra("BIKE_ID");
        if (bikeId == null) bikeId = bikeModel;

        if(tvBikeName != null) tvBikeName.setText(bikeModel);

        startTime = System.currentTimeMillis();
        currentRideDocId = "ride_" + startTime; // Generate Unique ID based on time

        // 2. CREATE RIDE DOCUMENT IN FIRESTORE (The Schema for any user)
        Map<String, Object> rideData = new HashMap<>();
        rideData.put("rideId", currentRideDocId);
        rideData.put("userId", userId);
        rideData.put("bikeId", bikeId);
        rideData.put("status", "Active");
        rideData.put("startTime", startTime);
        rideData.put("startStation", "UPH Medan Station"); // Default or Dynamic
        rideData.put("initialCost", 0);
        rideData.put("paymentMethod", paymentMethod);

        // Save to 'rides' collection
        db.collection("rides").document(currentRideDocId).set(rideData);

        // 3. UPDATE USER STATE (Links user to this specific ride)
        Map<String, Object> userUpdates = new HashMap<>();
        userUpdates.put("isActiveRide", true);
        userUpdates.put("currentRideId", currentRideDocId);

        db.collection("users").document(userId).set(userUpdates, SetOptions.merge());

        // 4. Update Realtime DB (Backup/Legacy)
        rtDbRef = FirebaseDatabase.getInstance().getReference("activeRides").child(userId);
        rtDbRef.child("isActive").setValue(true);
        rtDbRef.child("startTime").setValue(startTime);
        rtDbRef.child("bikeModel").setValue(bikeModel);

        saveLocalState();
        startTimer();
    }

    private void resumeRideState() {
        SharedPreferences prefs = getSharedPreferences("GowesAppPrefs", Context.MODE_PRIVATE);
        startTime = prefs.getLong("ride_start_time", 0);
        bikeModel = prefs.getString("active_bike_model", "Gowes Electric Bike");
        paymentMethod = prefs.getString("active_payment_method", "Wallet");

        if (startTime == 0) {
            // Fetch from Firestore if local data lost
            db.collection("users").document(userId).get().addOnSuccessListener(snapshot -> {
                if (snapshot.exists() && Boolean.TRUE.equals(snapshot.getBoolean("isActiveRide"))) {
                    // Get the ID of the ride to resume
                    currentRideDocId = snapshot.getString("currentRideId");
                    // Ideally fetch startTime from the 'rides' collection using currentRideDocId
                    // For simplicity, we restart if fails, but in prod fetch the doc.
                    startTime = System.currentTimeMillis();
                    startTimer();
                } else {
                    finish();
                }
            });
        } else {
            if(tvBikeName != null) tvBikeName.setText(bikeModel);
            startTimer();
        }
    }

    private void saveLocalState() {
        SharedPreferences prefs = getSharedPreferences("GowesAppPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong("ride_start_time", startTime);
        editor.putString("active_bike_model", bikeModel);
        editor.putString("active_payment_method", paymentMethod);
        editor.apply();
    }

    private void startTimer() {
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                timeInMilliseconds = System.currentTimeMillis() - startTime;
                if (timeInMilliseconds < 0) timeInMilliseconds = 0;

                long totalSeconds = timeInMilliseconds / 1000;
                updateTimerUI(totalSeconds);

                long totalBlocks = (long) Math.ceil(totalSeconds / SECONDS_PER_BLOCK);
                if (totalBlocks == 0) totalBlocks = 1;
                finalCalculatedCost = totalBlocks * RATE_PER_BLOCK;
                updateCostUI(finalCalculatedCost);

                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.postDelayed(timerRunnable, 0);
    }

    private void updateTimerUI(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
    }

    private void updateCostUI(double cost) {
        tvCurrentCost.setText(formatCurrency(cost));
    }

    private void showDetectingDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_detecting_bike);
        dialog.setCancelable(false);
        if (dialog.findViewById(R.id.btn_cancel) != null) {
            dialog.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
                dialog.dismiss();
                resumeTimerAfterCancel();
            });
        }
        dialog.show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
                showBikeDetectedDialog();
            }
        }, 3000);
    }

    private void showBikeDetectedDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_bike_detected);
        dialog.setCancelable(false);
        if (dialog.findViewById(R.id.btn_take_photo) != null) {
            dialog.findViewById(R.id.btn_take_photo).setOnClickListener(v -> {
                dialog.dismiss();
                Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraResultLauncher.launch(cameraIntent);
            });
        }
        dialog.show();
    }

    private void resumeTimerAfterCancel() {
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.postDelayed(timerRunnable, 0);
        }
    }

    private void endRide() {
        // 1. Update Firestore User
        Map<String, Object> updates = new HashMap<>();
        updates.put("isActiveRide", false);
        updates.put("currentRideId", null);
        db.collection("users").document(userId).update(updates);

        // 2. Update Ride Document Status
        // If we don't have currentRideDocId in memory (e.g. app restart), we should fetch it.
        // Assuming normal flow for now.
        if (currentRideDocId == null) currentRideDocId = "ride_" + startTime;
        db.collection("rides").document(currentRideDocId).update("status", "Completed");

        // 3. Clear Realtime DB & Local
        if (rtDbRef == null) rtDbRef = FirebaseDatabase.getInstance().getReference("activeRides").child(userId);
        rtDbRef.removeValue();

        SharedPreferences prefs = getSharedPreferences("GowesAppPrefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();

        long durationMs = timeInMilliseconds;
        int baseCharge = (int) Math.round(finalCalculatedCost);
        double co2Grams = (durationMs / 60000.0) * (1.1 / 45.0) * 1000.0;

        Intent intent = new Intent(ActiveRideActivity.this, RideCompleteActivity.class);
        intent.putExtra(RideCompleteActivity.EXTRA_RIDE_DURATION_MS, durationMs);
        intent.putExtra(RideCompleteActivity.EXTRA_BASE_CHARGE, baseCharge);
        intent.putExtra(RideCompleteActivity.EXTRA_PAYMENT_METHOD, paymentMethod);
        intent.putExtra(RideCompleteActivity.EXTRA_CO2_SAVED, co2Grams);
        intent.putExtra(RideCompleteActivity.EXTRA_BIKE_ID, bikeModel);

        startActivity(intent);
        finish();
    }

    private String formatCurrency(double amount) {
        Locale localeID = new Locale("in", "ID");
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(localeID);
        currencyFormatter.setMaximumFractionDigits(0);
        return currencyFormatter.format(amount);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }
}