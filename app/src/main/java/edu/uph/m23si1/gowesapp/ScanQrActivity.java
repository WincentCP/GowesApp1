package edu.uph.m23si1.gowesapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// --- IMPORT MLKIT BARU ---
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture; // Diperlukan untuk cameraProviderFuture
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

// --- IMPORT FIREBASE ---
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.camera.core.ExperimentalGetImage
public class ScanQrActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "ScanQrActivity";

    private PreviewView cameraPreview;
    private ConstraintLayout scannerUiLayout;
    private ConstraintLayout warningLayout;
    private Button btnGoBack;
    private ImageView ivBack;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner; // ML Kit Scanner

    private boolean isScanningPaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_qr);

        // Pastikan ID ini (camera_preview, scanner_ui_layout, dll.)
        // cocok dengan file layout/activity_scan_qr.xml Anda.
        cameraPreview = findViewById(R.id.camera_preview);
        scannerUiLayout = findViewById(R.id.scanner_ui_layout);
        warningLayout = findViewById(R.id.warning_layout);
        btnGoBack = findViewById(R.id.btn_go_back);
        ivBack = findViewById(R.id.iv_back);

        ivBack.setOnClickListener(v -> finish());
        btnGoBack.setOnClickListener(v -> finish());

        // Periksa izin kamera
        if (isCameraPermissionGranted()) {
            startCamera();
        } else {
            requestCameraPermission();
        }

        // Cek status perjalanan aktif dari Firestore (Perbaikan Bug 3)
        checkActiveRideStatus();
    }

    private void checkActiveRideStatus() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish(); // Tidak ada pengguna, kembali
            return;
        }

        DocumentReference userDoc = FirebaseFirestore.getInstance().collection("users").document(user.getUid());
        userDoc.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Boolean isActiveRide = documentSnapshot.getBoolean("isActiveRide");
                if (Boolean.TRUE.equals(isActiveRide)) {
                    // Jika perjalanan aktif, tunjukkan peringatan
                    showWarningLayout();
                } else {
                    // Jika tidak ada perjalanan, mulai pemindai
                    showScannerLayout();
                }
            }
        });
    }

    private boolean isCameraPermissionGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void startCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor();

        // --- Inisialisasi ML Kit Scanner ---
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        // --- Selesai Inisialisasi ---

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error starting camera", e);
                Toast.makeText(this, "Error starting camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        // 1. Set up Preview
        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(1280, 720))
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

        // 2. Set up ImageAnalysis untuk pemindaian QR
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        // --- INI ADALAH LOGIKA PEMINDAI QR YANG BARU ---
        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (isScanningPaused || imageProxy.getImage() == null) {
                imageProxy.close();
                return;
            }

            // Ubah ImageProxy ke InputImage ML Kit
            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

            // Proses gambar menggunakan ML Kit
            Task<List<Barcode>> result = barcodeScanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (!barcodes.isEmpty()) {
                            isScanningPaused = true; // Hentikan pemindaian sementara
                            String qrCodeValue = barcodes.get(0).getRawValue();
                            Log.d(TAG, "QR Code detected: " + qrCodeValue);

                            // FIX: Validasi QR Code sebelum melanjutkan
                            if (qrCodeValue != null && qrCodeValue.startsWith("GOWES_BIKE_")) {
                                // QR Code valid, kirim ID motor ke BikeDetailsActivity
                                Intent intent = new Intent(ScanQrActivity.this, BikeDetailsActivity.class);
                                intent.putExtra("BIKE_ID", qrCodeValue);
                                startActivity(intent);
                                finish(); // Tutup pemindai
                            } else {
                                // QR Code tidak valid
                                Toast.makeText(this, "Invalid QR Code. Please scan a valid Gowes bike.", Toast.LENGTH_SHORT).show();
                                isScanningPaused = false; // Izinkan pemindaian lagi
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Barcode scanning failed", e))
                    .addOnCompleteListener(task -> imageProxy.close()); // Selalu tutup imageProxy
        });
        // --- AKHIR LOGIKA PEMINDAI BARU ---

        // Bind semua use case ke kamera
        try {
            cameraProvider.unbindAll(); // Unbind case sebelumnya
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void showWarningLayout() {
        scannerUiLayout.setVisibility(View.GONE);
        warningLayout.setVisibility(View.VISIBLE);
    }

    private void showScannerLayout() {
        scannerUiLayout.setVisibility(View.VISIBLE);
        warningLayout.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (barcodeScanner != null) {
            // Penting: Tutup scanner untuk membebaskan resource
            barcodeScanner.close();
        }
    }
}