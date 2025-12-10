package edu.uph.m23si1.gowesapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class RidesFragment extends Fragment {

    private static final String TAG = "RidesFragment";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration ridesListener;
    private ListenerRegistration statsListener;

    private RecyclerView rvRecentRides;
    private RideAdapter rideAdapter;
    private List<RideModel> rideList;
    private TextView tvNoRides;
    private TextView tvTotalRides, tvTimeSaved, tvCo2Saved;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rides, container, false);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        MaterialButton btnScanQr = view.findViewById(R.id.btn_scan_qr);
        TextView tvViewAll = view.findViewById(R.id.tv_view_all);
        tvNoRides = view.findViewById(R.id.tv_no_rides);

        tvTotalRides = view.findViewById(R.id.tv_total_rides);
        tvTimeSaved = view.findViewById(R.id.tv_time_saved);
        tvCo2Saved = view.findViewById(R.id.tv_co2_saved);

        rvRecentRides = view.findViewById(R.id.rv_recent_rides);
        rvRecentRides.setLayoutManager(new LinearLayoutManager(getContext()));
        rideList = new ArrayList<>();
        rideAdapter = new RideAdapter(rideList);
        rvRecentRides.setAdapter(rideAdapter);

        if (btnScanQr != null) {
            btnScanQr.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), ScanQrActivity.class);
                startActivity(intent);
            });
        }

        // Optional: Klik View All untuk melihat history lengkap
        if (tvViewAll != null) {
            tvViewAll.setOnClickListener(v -> {
                Toast.makeText(getContext(), "View All clicked", Toast.LENGTH_SHORT).show();
            });
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            loadUserStats(user.getUid());
            loadRecentRides(user.getUid());
        }
    }

    private void loadUserStats(String userId) {
        statsListener = db.collection("users").document(userId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        Double rides = snapshot.getDouble("stats.totalRides");
                        Double co2 = snapshot.getDouble("stats.totalCO2Saved");

                        int r = (rides != null) ? rides.intValue() : 0;
                        double c = (co2 != null) ? co2 : 0.0;
                        double hoursSaved = r * 0.33;

                        if (tvTotalRides != null) tvTotalRides.setText(String.valueOf(r));
                        if (tvTimeSaved != null) tvTimeSaved.setText(String.format("%.1fh", hoursSaved));
                        if (tvCo2Saved != null) tvCo2Saved.setText(String.format("%.1fkg", c));
                    }
                });
    }

    private void loadRecentRides(String userId) {
        // PERBAIKAN: Kembali ke path users -> rideHistory.
        // Jika data sewa tidak muncul, pastikan dokumen di Firestore memiliki field 'timestamp'.
        Query query = db.collection("users").document(userId).collection("rideHistory")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5);

        ridesListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                // Kita tidak memerlukan index khusus untuk query subcollection ini
                // kecuali jika ada filter tambahan.
                return;
            }

            rideList.clear();
            if (snapshots != null && !snapshots.isEmpty()) {
                for (DocumentSnapshot doc : snapshots) {
                    RideModel ride = doc.toObject(RideModel.class);
                    // Validasi agar tidak crash jika ada data korup
                    if (ride != null) {
                        rideList.add(ride);
                    }
                }
                // Update UI: Tampilkan list, sembunyikan pesan kosong
                rvRecentRides.setVisibility(View.VISIBLE);
                tvNoRides.setVisibility(View.GONE);
            } else {
                // Update UI: Sembunyikan list, tampilkan pesan kosong
                rvRecentRides.setVisibility(View.GONE);
                tvNoRides.setVisibility(View.VISIBLE);
            }

            rideAdapter.notifyDataSetChanged();
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (ridesListener != null) ridesListener.remove();
        if (statsListener != null) statsListener.remove();
    }
}