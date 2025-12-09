package edu.uph.m23si1.gowesapp;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ConfirmRideActivity extends AppCompatActivity {

    private static final String TAG = "ConfirmRideActivity";
    private TextView tvBikeName, tvWalletBalance, btnInlineTopUp;
    private TextView tvRate;
    private MaterialButton btnConfirm;
    private ImageView btnBack;

    // Payment UI
    private CardView payAsYouGoCard, useWalletCard;
    private ImageView radioBtnPayG, radioBtnWallet;
    private boolean isWalletSelected = false;

    private String bikeId;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private long currentBalance = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm_your_ride);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize UI Views
        tvBikeName = findViewById(R.id.tv_bike_name);
        tvRate = findViewById(R.id.tv_rate);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnBack = findViewById(R.id.iv_back);

        payAsYouGoCard = findViewById(R.id.pay_as_you_go_card);
        useWalletCard = findViewById(R.id.use_wallet_card);
        radioBtnPayG = findViewById(R.id.radio_btn_payg);
        radioBtnWallet = findViewById(R.id.radio_btn_wallet);
        tvWalletBalance = findViewById(R.id.tv_wallet_balance);
        btnInlineTopUp = findViewById(R.id.btn_inline_top_up); // Note: Make sure this ID exists in XML if not found

        // Retrieve Data from Intent
        String selectedBikeId = getIntent().getStringExtra("SELECTED_BIKE_ID");
        if (selectedBikeId != null && !selectedBikeId.isEmpty()) {
            bikeId = selectedBikeId;
        } else {
            bikeId = "BK-UNKNOWN";
        }

        if (tvBikeName != null) {
            tvBikeName.setText("Bike " + bikeId);
        }

        setupPaymentSelection();
        fetchWalletBalance();

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> startRide());
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Top Up Action - check if button exists in layout first
        if (btnInlineTopUp != null) {
            btnInlineTopUp.setOnClickListener(v -> showTopUpDialog());
        }
    }

    private void fetchWalletBalance() {
        if (mAuth.getCurrentUser() == null) return;

        db.collection("users").document(mAuth.getCurrentUser().getUid())
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        Long balance = snapshot.getLong("walletBalance");
                        if (balance != null) {
                            currentBalance = balance;
                            if (tvWalletBalance != null) {
                                tvWalletBalance.setText("Balance: Rp " + currentBalance);
                            }
                        }
                    }
                });
    }

    private void setupPaymentSelection() {
        payAsYouGoCard.setOnClickListener(v -> {
            isWalletSelected = false;
            updatePaymentUI();
        });

        useWalletCard.setOnClickListener(v -> {
            isWalletSelected = true;
            updatePaymentUI();
        });
        updatePaymentUI();
    }

    private void updatePaymentUI() {
        if (isWalletSelected) {
            payAsYouGoCard.setBackgroundResource(R.drawable.card_payment_unselected);
            useWalletCard.setBackgroundResource(R.drawable.card_payment_selected);
            radioBtnPayG.setImageResource(R.drawable.ic_radio_button_unchecked);
            radioBtnWallet.setImageResource(R.drawable.ic_radio_button_checked);
        } else {
            payAsYouGoCard.setBackgroundResource(R.drawable.card_payment_selected);
            useWalletCard.setBackgroundResource(R.drawable.card_payment_unselected);
            radioBtnPayG.setImageResource(R.drawable.ic_radio_button_checked);
            radioBtnWallet.setImageResource(R.drawable.ic_radio_button_unchecked);
        }
    }

    private void showTopUpDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_top_up_balance);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        EditText etAmount = dialog.findViewById(R.id.et_amount);
        MaterialButton btnPay = dialog.findViewById(R.id.btn_pay_now);

        // Setup preset buttons
        setupPresetButton(dialog, R.id.btn_amount_30k, 30000, etAmount);
        setupPresetButton(dialog, R.id.btn_amount_50k, 50000, etAmount);
        setupPresetButton(dialog, R.id.btn_amount_100k, 100000, etAmount);
        setupPresetButton(dialog, R.id.btn_amount_200k, 200000, etAmount);

        MaterialButton btnCancel = dialog.findViewById(R.id.btn_cancel);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (btnPay != null) {
            btnPay.setOnClickListener(v -> {
                if (etAmount != null) {
                    String amountStr = etAmount.getText().toString();
                    if (!amountStr.isEmpty()) {
                        long amount = Long.parseLong(amountStr);
                        performTopUp(amount);
                        dialog.dismiss();
                    } else {
                        Toast.makeText(this, "Enter amount", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        dialog.show();
    }

    private void setupPresetButton(Dialog dialog, int btnId, long amount, EditText etAmount) {
        MaterialButton btn = dialog.findViewById(btnId);
        if (btn != null && etAmount != null) {
            btn.setOnClickListener(v -> etAmount.setText(String.valueOf(amount)));
        }
    }

    private void performTopUp(long amount) {
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();

        // Update Firestore
        db.collection("users").document(userId)
                .update("walletBalance", currentBalance + amount)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Top up successful!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Top up failed", Toast.LENGTH_SHORT).show());
    }

    private void startRide() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate Wallet Balance if Wallet is selected
        if (isWalletSelected && currentBalance < 25000) {
            Toast.makeText(this, "Insufficient wallet balance (Min Rp 25.000). Please top up.", Toast.LENGTH_LONG).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        long startTime = System.currentTimeMillis();
        String rideId = "ride_" + startTime;

        Map<String, Object> rideData = new HashMap<>();
        rideData.put("rideId", rideId);
        rideData.put("userId", userId);
        rideData.put("bikeId", bikeId);
        rideData.put("startTime", startTime);
        rideData.put("status", "Active");
        rideData.put("startStation", "UPH Medan Station");
        rideData.put("paymentMethod", isWalletSelected ? "Wallet" : "PayAsYouGo");
        rideData.put("initialCost", 0);

        // Attempt to start ride
        db.collection("rides").document(rideId)
                .set(rideData)
                .addOnSuccessListener(aVoid -> updateUserStatus(userId, rideId, bikeId, startTime))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Start ride failed", e);
                    // Show exact error to user for debugging
                    Toast.makeText(ConfirmRideActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void updateUserStatus(String userId, String rideId, String bikeId, long startTime) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("isActiveRide", true);
        updates.put("currentRideId", rideId);

        db.collection("users").document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Intent intent = new Intent(ConfirmRideActivity.this, ActiveRideActivity.class);
                    intent.putExtra("RIDE_ID", rideId);
                    intent.putExtra("BIKE_ID", bikeId);
                    intent.putExtra("START_TIME", startTime);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Update user failed", e));
    }
}