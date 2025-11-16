package edu.uph.m23si1.gowesapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button; // Ditambahkan
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Layout ini sekarang menggunakan XML baru Anda yang lebih lengkap
        setContentView(R.layout.activity_splash);

        // Cek status login SAAT INI
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            // Jika sudah login, langsung ke MainActivity dan lewati sisa logic
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
            return; // Penting: Hentikan eksekusi di sini
        }

        // --- Jika TIDAK login, biarkan user memilih ---

        // Inisialisasi tombol dari layout XML Anda
        // FIX: Menggunakan ID yang benar dari activity_splash.xml Anda
        Button btnLogin = findViewById(R.id.btn_login);
        Button btnCreateAccount = findViewById(R.id.btn_create_account);

        // Atur listener untuk tombol Login
        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            // Kita tidak memanggil finish() agar user bisa kembali ke layar ini
        });

        // Atur listener untuk tombol Create Account
        btnCreateAccount.setOnClickListener(v -> {
            startActivity(new Intent(SplashActivity.this, CreateAccountActivity.class));
            // Kita tidak memanggil finish() agar user bisa kembali ke layar ini
        });
    }
}