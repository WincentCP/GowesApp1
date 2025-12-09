package edu.uph.m23si1.gowesapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileFragment extends Fragment {

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private TextView tvStatRides, tvStatCo2, tvUserName, tvUserPhone;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        tvStatRides = view.findViewById(R.id.tv_stat_rides);
        tvStatCo2 = view.findViewById(R.id.tv_stat_co2);
        tvUserName = view.findViewById(R.id.tv_user_name);
        tvUserPhone = view.findViewById(R.id.tv_user_phone);

        // Logout logic
        View btnLogout = view.findViewById(R.id.btn_logout);
        if(btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                mAuth.signOut();
                startActivity(new Intent(getActivity(), LoginActivity.class));
                getActivity().finish();
            });
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mAuth.getCurrentUser() != null) {
            loadUserProfile(mAuth.getCurrentUser().getUid());
        }
    }

    private void loadUserProfile(String userId) {
        db.collection("users").document(userId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        // Profile Info
                        String name = snapshot.getString("fullName");
                        String phone = snapshot.getString("phone");
                        tvUserName.setText(name != null ? name : "User");
                        tvUserPhone.setText(phone != null ? phone : "-");

                        // Stats Sync (Same logic as RidesFragment)
                        Double rides = snapshot.getDouble("stats.totalRides");
                        Double co2 = snapshot.getDouble("stats.totalCO2Saved");

                        int r = (rides != null) ? rides.intValue() : 0;
                        double c = (co2 != null) ? co2 : 0.0;

                        tvStatRides.setText(String.valueOf(r));
                        tvStatCo2.setText(String.format("%.1fkg", c));
                    }
                });
    }
}