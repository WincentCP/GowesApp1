package edu.uph.m23si1.gowesapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

public class BikeDetailsActivity extends AppCompatActivity {

    private RadioGroup rgBikeSelection;
    private TextView tvBatteryLevel, tvRange, tvStatus;
    private MaterialButton btnRentBike;
    private ImageView btnBack;
    private String selectedBikeId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bike_details);

        rgBikeSelection = findViewById(R.id.rg_bike_selection);
        tvBatteryLevel = findViewById(R.id.tv_battery_level);
        tvRange = findViewById(R.id.tv_range);
        tvStatus = findViewById(R.id.tv_status);
        btnRentBike = findViewById(R.id.btn_rent_bike);
        btnBack = findViewById(R.id.btn_back);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // Handle Bike Selection - ALL BIKES AVAILABLE & SAME SPECS
        rgBikeSelection.setOnCheckedChangeListener((group, checkedId) -> {
            btnRentBike.setEnabled(true);
            btnRentBike.setAlpha(1.0f);

            if (checkedId == R.id.rb_bike_001) {
                updateBikeStats("BK-001");
            } else if (checkedId == R.id.rb_bike_002) {
                updateBikeStats("BK-002");
            } else if (checkedId == R.id.rb_bike_003) {
                updateBikeStats("BK-003");
            } else if (checkedId == R.id.rb_bike_004) {
                updateBikeStats("BK-004");
            }
        });

        btnRentBike.setOnClickListener(v -> {
            if (selectedBikeId != null) {
                Intent intent = new Intent(BikeDetailsActivity.this, ConfirmRideActivity.class);
                intent.putExtra("SELECTED_BIKE_ID", selectedBikeId);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Please select a bike", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateBikeStats(String bikeId) {
        selectedBikeId = bikeId;
        // Standardized stats for all bikes as requested
        tvBatteryLevel.setText("100%");
        tvRange.setText("60 km");
        tvStatus.setText("Available");
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
    }
}