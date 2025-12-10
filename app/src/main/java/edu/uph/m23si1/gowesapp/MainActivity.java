package edu.uph.m23si1.gowesapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fabScan;

    // Add Firebase instances
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        bottomNavigationView = findViewById(R.id.bottom_navigation_view);
        fabScan = findViewById(R.id.fab_scan);

        // Set the default fragment to Home
        if (savedInstanceState == null) {
            replaceFragment(new HomeFragment());
        }

        // Handle bottom navigation item selection
        bottomNavigationView.setOnItemSelectedListener(new BottomNavigationView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();

                if (itemId == R.id.nav_home) {
                    replaceFragment(new HomeFragment());
                    return true;
                } else if (itemId == R.id.nav_rides) {
                    replaceFragment(new RidesFragment());
                    return true;
                } else if (itemId == R.id.nav_wallet) {
                    replaceFragment(new WalletFragment());
                    return true;
                } else if (itemId == R.id.nav_profile) {
                    replaceFragment(new ProfileFragment());
                    return true;
                }
                return false;
            }
        });

        // UPDATED: Handle Scan FAB click with "Active Ride" Check
        fabScan.setOnClickListener(v -> checkActiveRideAndScan());

        // This is a small trick to make the "Scan" placeholder in the menu un-selectable
        View placeholderItem = findViewById(R.id.nav_placeholder);
        if (placeholderItem != null) {
            placeholderItem.setClickable(false);
        }
    }

    private void checkActiveRideAndScan() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        // Check Firestore to see if user is already riding
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Boolean isActive = snapshot.getBoolean("isActiveRide");

                        if (Boolean.TRUE.equals(isActive)) {
                            // 🛑 BLOCK: User has an active ride
                            Toast.makeText(MainActivity.this, "You have an ongoing ride!", Toast.LENGTH_SHORT).show();

                            // Redirect to Active Ride page to Resume
                            Intent intent = new Intent(MainActivity.this, ActiveRideActivity.class);
                            intent.putExtra("IS_NEW_RIDE", false); // Not new, it's a resume
                            startActivity(intent);
                        } else {
                            // ✅ ALLOW: Open Scanner
                            startActivity(new Intent(MainActivity.this, ScanQrActivity.class));
                        }
                    } else {
                        // Safe fallback if user doc missing
                        startActivity(new Intent(MainActivity.this, ScanQrActivity.class));
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "Network error. Please try again.", Toast.LENGTH_SHORT).show();
                });
    }

    // Helper method to replace the fragment in the container
    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commit();
    }
}