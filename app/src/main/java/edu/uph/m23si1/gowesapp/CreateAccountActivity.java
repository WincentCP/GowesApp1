package edu.uph.m23si1.gowesapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CreateAccountActivity extends AppCompatActivity {

    private static final String TAG = "CreateAccountActivity";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Deklarasi semua komponen dari XML baru
    private EditText etName, etPhone, etEmail, etPassword, etConfirmPassword;
    private Button btnCreateAccount;
    private ProgressBar progressBar;
    private ImageView ivBack;
    private TextView tvLoginLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account); // Menggunakan XML yang sudah diperbarui

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Inisialisasi semua komponen dari XML baru
        etName = findViewById(R.id.et_name);
        etPhone = findViewById(R.id.et_phone);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        btnCreateAccount = findViewById(R.id.btn_create_account);
        progressBar = findViewById(R.id.progressBar);
        ivBack = findViewById(R.id.iv_back);
        tvLoginLink = findViewById(R.id.tv_login_link);

        // (Bug #3) Fungsikan tombol kembali - INI TETAP, KARENA INILAH FUNGSI BACK
        ivBack.setOnClickListener(v -> finish());

        // (Bug #2) Fungsikan link "Login here"
        tvLoginLink.setOnClickListener(v -> {
            startActivity(new Intent(CreateAccountActivity.this, LoginActivity.class));
            // finish(); // DIHAPUS: Sesuai permintaan Anda, 'finish()' dihapus agar bisa kembali.
        });

        // Listener untuk tombol create account
        btnCreateAccount.setOnClickListener(v -> createAccount());
    }

    private void createAccount() {
        // Ambil semua data dari form
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Validasi input
        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required.");
            etName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            etPhone.setError("Phone is required.");
            etPhone.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required.");
            etEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required.");
            etPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters.");
            etPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords  do not match.");
            etConfirmPassword.requestFocus();
            return;
        }

        // Tampilkan loading
        progressBar.setVisibility(View.VISIBLE);
        btnCreateAccount.setEnabled(false);

        // (Bug #4) Gunakan createUserWithEmailAndPassword
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "createUserWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                // Simpan data tambahan ke Firestore
                                saveUserDataToFirestore(user.getUid(), name, phone, email);
                            }
                        } else {
                            // (Bug #4) Tampilkan pesan error Firebase yang spesifik
                            String errorMessage = "Authentication failed.";
                            if (task.getException() != null) {
                                errorMessage = task.getException().getMessage();
                                Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            }
                            Toast.makeText(CreateAccountActivity.this, errorMessage, Toast.LENGTH_LONG).show();

                            // Sembunyikan loading dan aktifkan tombol kembali
                            progressBar.setVisibility(View.GONE);
                            btnCreateAccount.setEnabled(true);
                        }
                    }
                });
    }

    private void saveUserDataToFirestore(String userId, String name, String phone, String email) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", name);
        userData.put("phone", phone);
        userData.put("email", email); // Simpan email juga
        userData.put("walletBalance", 0);
        userData.put("isCardLinked", false);
        userData.put("isActiveRide", false);
        userData.put("identityVerified", false); // Status verifikasi baru

        db.collection("users").document(userId)
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "DocumentSnapshot successfully written!");
                    progressBar.setVisibility(View.GONE); // Sembunyikan loading

                    // Arahkan ke MainActivity setelah berhasil
                    Intent intent = new Intent(CreateAccountActivity.this, MainActivity.class);

                    // DIHAPUS: Sesuai permintaan Anda, flags dan finish() dihapus.
                    // intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    // finish();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error writing document", e);
                    progressBar.setVisibility(View.GONE); // Sembunyikan loading
                    btnCreateAccount.setEnabled(true); // Aktifkan tombol
                    Toast.makeText(CreateAccountActivity.this, "Failed to save user data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}