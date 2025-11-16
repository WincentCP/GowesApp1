package edu.uph.m23si1.gowesapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot; // FIX: Added missing import

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ActiveRideActivity extends AppCompatActivity {

    private static final String TAG = "ActiveRideActivity";

    // (Perbaikan Bug 10)
    private static final double RATE_PER_BLOCK = 8000.0;
    private static final double SECONDS_PER_BLOCK = 3600.0; // 60 menit

    private TextView tvTimer, tvCurrentCost;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTime = 0;
    private long timeInMilliseconds = 0;

    private String finalRideDuration;
    private double finalCalculatedCost = 0.0;

    private FirebaseFirestore db;
    private DatabaseReference rtDbRef; // Realtime Database untuk data live
    private String userId;

    // (Perbaikan Bug 11)
    private final ActivityResultLauncher<Intent> cameraResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(this, "Photo captured!", Toast.LENGTH_SHORT).show();
                    endRide();
                } else {
                    Toast.makeText(this, "Photo cancelled. Please take photo to end ride.", Toast.LENGTH_SHORT).show();
                }
            });

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            timeInMilliseconds = System.currentTimeMillis() - startTime;
            long totalSeconds = timeInMilliseconds / 1000;
            updateTimerUI(totalSeconds);

            // (Perbaikan Bug 10)
            long totalBlocks = (long) Math.ceil(totalSeconds / SECONDS_PER_BLOCK);
            if (totalBlocks == 0) totalBlocks = 1;
            finalCalculatedCost = totalBlocks * RATE_PER_BLOCK;
            updateCostUI(finalCalculatedCost);

            // Update Realtime Database
            if (rtDbRef != null) {
                rtDbRef.child("currentTime").setValue(totalSeconds);
                rtDbRef.child("currentCost").setValue(finalCalculatedCost);
            }

            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_ride);

        db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish(); // Tidak ada pengguna, tutup
            return;
        }
        userId = user.getUid();

        // Gunakan Realtime Database untuk data live (timer, lokasi, dll.)
        rtDbRef = FirebaseDatabase.getInstance().getReference("activeRides").child(userId);

        // (Perbaikan Bug 3) Set perjalanan aktif di Firestore & Realtime DB
        db.collection("users").document(userId).update("isActiveRide", true);
        rtDbRef.child("isActive").setValue(true);
        rtDbRef.child("startTime").setValue(System.currentTimeMillis());

        tvTimer = findViewById(R.id.tv_timer);
        tvCurrentCost = findViewById(R.id.tv_current_cost);
        Button parkButton = findViewById(R.id.btn_park);
        Button backHomeButton = findViewById(R.id.btn_back_home);

        parkButton.setOnClickListener(v -> {
            timerHandler.removeCallbacks(timerRunnable);
            finalRideDuration = tvTimer.getText().toString();
            showDetectingDialog();
        });

        backHomeButton.setOnClickListener(v -> finish());

        startTime = System.currentTimeMillis();
        timerHandler.postDelayed(timerRunnable, 0);
    }

    // ... (updateTimerUI dan updateCostUI tidak berubah) ...
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
        // ... (Logika dialog tidak berubah) ...
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_detecting_bike);
        dialog.setCancelable(false);
        dialog.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            dialog.dismiss();
            startTime = System.currentTimeMillis() - timeInMilliseconds;
            timerHandler.postDelayed(timerRunnable, 0);
        });
        dialog.show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
                showBikeDetectedDialog();
            }
        }, 3000);
    }

    private void showBikeDetectedDialog() {
        // (Perbaikan Bug 11)
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_bike_detected);
        dialog.setCancelable(false);
        dialog.findViewById(R.id.btn_take_photo).setOnClickListener(v -> {
            dialog.dismiss();
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraResultLauncher.launch(cameraIntent);
        });
        dialog.show();
    }

    private void endRide() {
        // (Perbaikan Bug 3)
        db.collection("users").document(userId).update("isActiveRide", false);
        rtDbRef.removeValue(); // Hapus data dari Realtime Database

        // Kurangi saldo dompet di Firestore
        DocumentReference userDocRef = db.collection("users").document(userId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(userDocRef);
            Number currentBalanceNum = (Number) snapshot.get("walletBalance");
            int currentBalance = (currentBalanceNum != null) ? currentBalanceNum.intValue() : 0;
            int newBalance = currentBalance - (int) finalCalculatedCost;
            transaction.update(userDocRef, "walletBalance", newBalance);
            return null; // Tidak perlu mengembalikan apa-apa
        }).addOnFailureListener(e -> Log.w(TAG, "Gagal mengurangi saldo", e));

        // (Perbaikan Bug 12) Simpan riwayat perjalanan ke sub-koleksi
        Map<String, Object> rideHistory = new HashMap<>();
        rideHistory.put("duration", finalRideDuration);
        rideHistory.put("cost", finalCalculatedCost);
        rideHistory.put("timestamp", System.currentTimeMillis());

        db.collection("users").document(userId).collection("rideHistory")
                .add(rideHistory)
                .addOnSuccessListener(docRef -> Log.d(TAG, "Riwayat perjalanan disimpan"))
                .addOnFailureListener(e -> Log.w(TAG, "Gagal simpan riwayat", e));

        Intent intent = new Intent(ActiveRideActivity.this, RideCompleteActivity.class);
        intent.putExtra("RIDE_DURATION", finalRideDuration);
        intent.putExtra("FINAL_COST", finalCalculatedCost);
        intent.putExtra("TOTAL_SECONDS", timeInMilliseconds / 1000);

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
        timerHandler.removeCallbacks(timerRunnable);
    }
}