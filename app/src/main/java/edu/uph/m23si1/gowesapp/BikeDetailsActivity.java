package edu.uph.m23si1.gowesapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class BikeDetailsActivity extends AppCompatActivity {

    private static final String TAG = "BikeDetailsActivity";

    // UI Components
    private RadioGroup rgBikeSelection;
    private TextView tvBatteryLevel, tvRange, tvStatus;
    private Button btnRentBike;
    private ImageView btnBack;

    // Data Variables
    private String selectedBikeId = null;
    private String currentSlotKey = null; // Menyimpan "slot_1", "slot_2", dst
    private DatabaseReference slotsRef;

    // Listener agar realtime update bisa dimatikan saat activity close
    private ValueEventListener slotStatusListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bike_details);

        // 1. Inisialisasi Firebase (Gunakan URL jika perlu, atau default)
        try {
            // Ganti URL ini dengan URL database Anda sendiri jika getInstance() default gagal
            FirebaseDatabase database = FirebaseDatabase.getInstance("https://smartbike-c6082-default-rtdb.firebaseio.com/");
            slotsRef = database.getReference("stations")
                    .child("station_uph_medan")
                    .child("slots");
        } catch (Exception e) {
            Log.e(TAG, "Database Error: " + e.getMessage());
            Toast.makeText(this, "Database Config Error", Toast.LENGTH_SHORT).show();
        }

        // 2. Bind Views
        btnBack = findViewById(R.id.btn_back);
        rgBikeSelection = findViewById(R.id.rg_bike_selection);
        tvBatteryLevel = findViewById(R.id.tv_battery_level);
        tvRange = findViewById(R.id.tv_range);
        tvStatus = findViewById(R.id.tv_status);
        btnRentBike = findViewById(R.id.btn_rent_bike);

        // 3. Listener Tombol Back
        btnBack.setOnClickListener(v -> onBackPressed());

        // 4. Listener RadioGroup (Memilih Slot & Mulai Listening Statusnya)
        rgBikeSelection.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_bike_001) {
                selectSlot("BK-001", "slot_1", 88, 45); // Baterai/Range masih dummy, bisa diambil dr DB juga nanti
            } else if (checkedId == R.id.rb_bike_002) {
                selectSlot("BK-002", "slot_2", 92, 50);
            } else if (checkedId == R.id.rb_bike_003) {
                selectSlot("BK-003", "slot_3", 60, 30);
            } else if (checkedId == R.id.rb_bike_004) {
                selectSlot("BK-004", "slot_4", 45, 20);
            }
        });

        // 5. Listener Tombol Rent (Buka Servo)
        btnRentBike.setOnClickListener(v -> {
            if (currentSlotKey != null) {
                openServoForSlot(currentSlotKey);
            } else {
                Toast.makeText(this, "Pilih sepeda terlebih dahulu", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Fungsi ini dipanggil saat user memilih RadioButton.
     * Tugasnya: Memasang pendengar (Listener) ke path Firebase slot tersebut.
     */
    private void selectSlot(String bikeId, String slotKey, int defaultBat, int defaultRange) {
        this.selectedBikeId = bikeId;

        // Hapus listener lama jika ada (supaya tidak numpuk/bentrok)
        if (currentSlotKey != null && slotStatusListener != null) {
            slotsRef.child(currentSlotKey).removeEventListener(slotStatusListener);
        }

        this.currentSlotKey = slotKey;

        // Set info statis (Baterai/Range bisa dibuat realtime nanti jika ada datanya di DB)
        tvBatteryLevel.setText(defaultBat + "%");
        tvRange.setText(defaultRange + " km");
        tvStatus.setText("Loading..."); // Tanda sedang ambil data

        // --- REALTIME LISTENER ---
        slotStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Ambil status dari path: slots/slot_X/status
                // Arduino akan update ini jadi "Available" atau "In Use"
                String status = snapshot.child("status").getValue(String.class);

                if (status == null) status = "Unknown"; // Default jika data kosong

                updateUIStatus(status);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Gagal baca status: " + error.getMessage());
                tvStatus.setText("Error");
            }
        };

        // Pasang listener ke slot yang dipilih
        slotsRef.child(slotKey).addValueEventListener(slotStatusListener);
    }

    /**
     * Update UI (Warna teks & Enable/Disable Tombol) berdasarkan status Firebase
     */
    private void updateUIStatus(String status) {
        tvStatus.setText(status);

        if ("Available".equalsIgnoreCase(status)) {
            // HIJAU & BISA DISEWA
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.primaryOrange));
            btnRentBike.setEnabled(true);
            btnRentBike.setAlpha(1.0f);
            btnRentBike.setText("Proceed to Payment");
        } else {
            // ABU-ABU & TOMBOL MATI (Sedang dipakai / Sensor tidak mendeteksi sepeda)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            btnRentBike.setEnabled(false);
            btnRentBike.setAlpha(0.5f);
            btnRentBike.setText("Bike Unavailable");
        }
    }

    /**
     * Mengirim perintah BUKA ke Firebase
     */
    private void openServoForSlot(String slotKey) {
        btnRentBike.setEnabled(false);
        btnRentBike.setText("Unlocking...");

        // Tulis "OPEN" ke path servoStatus
        slotsRef.child(slotKey).child("servoStatus").setValue("OPEN")
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Request OPEN terkirim ke " + slotKey);
                    Toast.makeText(BikeDetailsActivity.this, "Payment Success! Unlocking...", Toast.LENGTH_SHORT).show();

                    // Delay untuk simulasi, lalu pindah halaman
                    new Handler().postDelayed(() -> {
                        goToActiveRide();
                    }, 2000);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Gagal kirim perintah", e);
                    Toast.makeText(BikeDetailsActivity.this, "Connection Failed", Toast.LENGTH_SHORT).show();
                    // Kembalikan tombol ke state awal (baca ulang status terakhir dari textview)
                    updateUIStatus(tvStatus.getText().toString());
                });
    }

    private void goToActiveRide() {
        // Hapus listener sebelum pindah agar memori aman
        if (currentSlotKey != null && slotStatusListener != null) {
            slotsRef.child(currentSlotKey).removeEventListener(slotStatusListener);
        }

        Intent intent = new Intent(BikeDetailsActivity.this, ActiveRideActivity.class);
        intent.putExtra("IS_NEW_RIDE", true);
        intent.putExtra("BIKE_MODEL", selectedBikeId);
        intent.putExtra(RideCompleteActivity.EXTRA_PAYMENT_METHOD, "Wallet");

        startActivity(intent);
        finish();
    }
}