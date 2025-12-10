package edu.uph.m23si1.gowesapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final int MIN_TOP_UP_AMOUNT = 30000;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration userListener;

    private ValueEventListener realtimeBikeListener;
    private DatabaseReference slotsRef;

    private CardView activeRideBanner;
    private TextView tvUserName, tvWalletBalance;
    private TextView tvTotalRides, tvTimeSaved, tvCo2Saved;
    private TextView tvAvailableBikesCount, tvBikeStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_home, container, false);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        try {
            FirebaseDatabase database = FirebaseDatabase.getInstance("https://smartbike-c6082-default-rtdb.firebaseio.com/");
            slotsRef = database.getReference("stations/station_uph_medan/slots");
        } catch (Exception e) {
            Log.e(TAG, "Database Error: " + e.getMessage());
        }

        activeRideBanner = view.findViewById(R.id.active_ride_banner);
        tvUserName = view.findViewById(R.id.tv_user_name);
        tvWalletBalance = view.findViewById(R.id.tv_wallet_balance_main);

        tvTotalRides = view.findViewById(R.id.tv_total_rides);
        tvTimeSaved = view.findViewById(R.id.tv_time_saved);
        tvCo2Saved = view.findViewById(R.id.tv_co2_saved);

        tvAvailableBikesCount = view.findViewById(R.id.tv_available_bikes_count);
        tvBikeStatus = view.findViewById(R.id.tv_bike_status);

        if (activeRideBanner != null) {
            activeRideBanner.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), ActiveRideActivity.class);
                intent.putExtra("IS_NEW_RIDE", false);
                startActivity(intent);
            });
        }

        View btnTopUp = view.findViewById(R.id.btn_top_up);
        if (btnTopUp != null) {
            btnTopUp.setOnClickListener(v -> showTopUpDialog());
        }

        setupRealtimeBikeCount();
        seedRTDBIfEmpty();

        return view;
    }

    private void setupRealtimeBikeCount() {
        if (slotsRef == null) return;

        realtimeBikeListener = slotsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = 0;
                long totalSlots = snapshot.getChildrenCount(); // Menghitung total slot (misal: 4)

                for (DataSnapshot slot : snapshot.getChildren()) {
                    String status = slot.child("status").getValue(String.class);
                    if ("Available".equalsIgnoreCase(status)) {
                        count++;
                    }
                }

                if (tvAvailableBikesCount != null) {
                    tvAvailableBikesCount.setText(String.valueOf(count));
                }

                // --- UPDATED LOGIC ---
                if (tvBikeStatus != null && getContext() != null) {
                    if (count == 0) {
                        // KOSONG
                        tvBikeStatus.setText("● No bikes available");
                        tvBikeStatus.setTextColor(ContextCompat.getColor(getContext(), android.R.color.holo_red_dark));
                    } else if (count == totalSlots && totalSlots > 0) {
                        // SEMUA TERSEDIA (Full)
                        tvBikeStatus.setText("● All bikes ready to ride");
                        tvBikeStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.primaryGreen));
                    } else {
                        // ADA SEBAGIAN (1, 2, 3...)
                        String text;
                        if (count == 1) {
                            text = "● 1 bike available";
                        } else {
                            text = "● " + count + " bikes available";
                        }
                        tvBikeStatus.setText(text);
                        // Tetap warna hijau karena masih tersedia
                        tvBikeStatus.setTextColor(ContextCompat.getColor(getContext(), R.color.primaryGreen));
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Gagal baca jumlah sepeda", error.toException());
            }
        });
    }

    private void seedRTDBIfEmpty() {
        if (slotsRef == null) return;

        slotsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Map<String, Object> updates = new HashMap<>();
                    for (int i = 1; i <= 4; i++) {
                        String slotKey = "slot_" + i;
                        Map<String, Object> slotData = new HashMap<>();
                        slotData.put("bikeId", "BK-00" + i);
                        slotData.put("servoStatus", "LOCKED");
                        slotData.put("status", "Available");
                        updates.put(slotKey, slotData);
                    }
                    slotsRef.updateChildren(updates);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void ensureUserDocumentExists(FirebaseUser user) {
        DocumentReference userDoc = db.collection("users").document(user.getUid());

        userDoc.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                Map<String, Object> newUser = new HashMap<>();
                newUser.put("email", user.getEmail());
                newUser.put("fullName", user.getDisplayName() != null ? user.getDisplayName() : "Goweser");
                newUser.put("walletBalance", 0);
                newUser.put("isActiveRide", false);
                newUser.put("currentRideId", null);

                Map<String, Object> stats = new HashMap<>();
                stats.put("totalRides", 0);
                stats.put("totalCO2Saved", 0.0);
                newUser.put("stats", stats);

                userDoc.set(newUser);
            } else {
                if (snapshot.get("stats") == null) {
                    Map<String, Object> stats = new HashMap<>();
                    stats.put("totalRides", 0);
                    stats.put("totalCO2Saved", 0.0);
                    userDoc.update("stats", stats);
                }
            }
        });
    }

    private void showTopUpDialog() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_top_up_balance);

        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnTopUp = dialog.findViewById(R.id.btn_top_up);
        EditText etAmount = dialog.findViewById(R.id.et_amount);

        View.OnClickListener presetListener = v -> {
            if (etAmount != null && v instanceof Button) {
                String text = ((Button) v).getText().toString();
                String clean = text.replaceAll("[^0-9]", "");
                etAmount.setText(clean);
                etAmount.setSelection(etAmount.getText().length());
            }
        };
        int[] presetIds = {R.id.btn_amount_30k, R.id.btn_amount_50k, R.id.btn_amount_100k, R.id.btn_amount_200k};
        for (int id : presetIds) {
            View btn = dialog.findViewById(id);
            if (btn != null) btn.setOnClickListener(presetListener);
        }

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());
        if (btnTopUp != null && etAmount != null) {
            btnTopUp.setOnClickListener(v -> {
                String amountStr = etAmount.getText().toString().trim();
                if (amountStr.isEmpty()) return;
                int amount = Integer.parseInt(amountStr);
                if (amount < MIN_TOP_UP_AMOUNT) {
                    Toast.makeText(getContext(), "Min Rp 30.000", Toast.LENGTH_SHORT).show();
                } else {
                    processTopUp(amount, dialog);
                }
            });
        }
        dialog.show();
    }

    private void processTopUp(int amount, BottomSheetDialog dialog) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        DocumentReference userRef = db.collection("users").document(user.getUid());

        userRef.update("walletBalance", com.google.firebase.firestore.FieldValue.increment(amount))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Top Up Success!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    Map<String, Object> txn = new HashMap<>();
                    txn.put("amount", amount);
                    txn.put("description", "Top Up Wallet");
                    txn.put("type", "TopUp");
                    txn.put("status", "Success");
                    txn.put("timestamp", System.currentTimeMillis());
                    userRef.collection("transactions").add(txn);
                })
                .addOnFailureListener(e -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("walletBalance", amount);
                    userRef.set(data, SetOptions.merge()).addOnSuccessListener(v -> dialog.dismiss());
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            ensureUserDocumentExists(user);
            startUserListener(user.getUid());
        }
    }

    private void startUserListener(String userId) {
        final DocumentReference userDocRef = db.collection("users").document(userId);

        userListener = userDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) return;

            String fullName = snapshot.getString("fullName");
            if (fullName != null && !fullName.isEmpty()) tvUserName.setText(fullName.split(" ")[0]);

            Number balance = (Number) snapshot.get("walletBalance");
            if (tvWalletBalance != null) tvWalletBalance.setText(formatCurrency(balance != null ? balance.intValue() : 0));

            Boolean isActive = snapshot.getBoolean("isActiveRide");
            activeRideBanner.setVisibility(Boolean.TRUE.equals(isActive) ? View.VISIBLE : View.GONE);

            Number rides = (Number) snapshot.get("stats.totalRides");
            Number co2 = (Number) snapshot.get("stats.totalCO2Saved");

            int totalRidesVal = (rides != null) ? rides.intValue() : 0;
            double totalCo2Val = (co2 != null) ? co2.doubleValue() : 0.0;

            if (tvTotalRides != null) tvTotalRides.setText(String.valueOf(totalRidesVal));
            if (tvCo2Saved != null) tvCo2Saved.setText(String.format(Locale.getDefault(), "%.1fkg", totalCo2Val));

            double hours = totalRidesVal * 0.33;
            if (tvTimeSaved != null) tvTimeSaved.setText(String.format(Locale.getDefault(), "%.1fh", hours));
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (userListener != null) userListener.remove();
        if (realtimeBikeListener != null && slotsRef != null) {
            slotsRef.removeEventListener(realtimeBikeListener);
        }
    }

    private String formatCurrency(int amount) {
        Locale localeID = new Locale("in", "ID");
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(localeID);
        currencyFormatter.setMaximumFractionDigits(0);
        return currencyFormatter.format(amount);
    }
}