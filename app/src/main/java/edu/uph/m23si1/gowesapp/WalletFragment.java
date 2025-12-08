package edu.uph.m23si1.gowesapp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WalletFragment extends Fragment {

    private static final String TAG = "WalletFragment";
    private static final int MIN_TOP_UP_AMOUNT = 30000;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration userListener;

    private TextView tvBalance, tvCardLast4;
    private CardView linkedCardView, linkPaymentView;
    private ImageView btnRemoveCard;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wallet, container, false);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        tvBalance = view.findViewById(R.id.tv_wallet_balance_main);
        linkedCardView = view.findViewById(R.id.linked_card_view);
        linkPaymentView = view.findViewById(R.id.link_payment_view);
        tvCardLast4 = view.findViewById(R.id.tv_card_last4);
        btnRemoveCard = view.findViewById(R.id.btn_remove_card);

        if (linkPaymentView != null) {
            linkPaymentView.setOnClickListener(v -> showLinkPaymentDialog());
        }

        View btnTopUpMain = view.findViewById(R.id.btn_top_up);
        if (btnTopUpMain != null) {
            btnTopUpMain.setOnClickListener(v -> showTopUpDialog());
        }

        if (btnRemoveCard != null) {
            btnRemoveCard.setOnClickListener(v -> confirmRemoveCard());
        }

        return view;
    }

    private void confirmRemoveCard() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Remove Card")
                .setMessage("Are you sure you want to remove this payment method?")
                .setPositiveButton("Remove", (dialog, which) -> removeCard())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeCard() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("isCardLinked", false);
            data.put("cardLast4", null); // Clear the number

            db.collection("users").document(user.getUid())
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Card Removed", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to remove card", Toast.LENGTH_SHORT).show());
        }
    }

    private void showLinkPaymentDialog() {
        if (getContext() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_link_payment);

        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnLink = dialog.findViewById(R.id.btn_link);
        EditText etCardNumber = dialog.findViewById(R.id.et_card_number);

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        if (btnLink != null) {
            btnLink.setOnClickListener(v -> {
                String cardNumber = "";
                if (etCardNumber != null) {
                    cardNumber = etCardNumber.getText().toString().replace(" ", "");
                }

                if (cardNumber.length() < 13) {
                    Toast.makeText(getContext(), "Invalid card number", Toast.LENGTH_SHORT).show();
                    return;
                }

                String last4 = cardNumber.substring(cardNumber.length() - 4);

                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("isCardLinked", true);
                    data.put("cardLast4", last4); // FIX: Save user input

                    db.collection("users").document(user.getUid())
                            .set(data, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(), "Card Linked Successfully!", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Link card failed", e);
                                Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                }
            });
        }
        dialog.show();
    }

    private void showTopUpDialog() {
        if (getContext() == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_top_up_balance);

        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnTopUp = dialog.findViewById(R.id.btn_top_up);
        EditText etAmount = dialog.findViewById(R.id.et_amount);

        // Preset Buttons logic
        View.OnClickListener presetListener = v -> {
            if (etAmount != null && v instanceof Button) {
                String text = ((Button) v).getText().toString(); // e.g. "Rp 30.000"
                String cleanAmount = text.replaceAll("[^0-9]", "");
                etAmount.setText(cleanAmount);
                etAmount.setSelection(etAmount.getText().length());
            }
        };

        int[] presetIds = {
                R.id.btn_amount_30k,
                R.id.btn_amount_50k,
                R.id.btn_amount_100k,
                R.id.btn_amount_200k
        };
        for (int id : presetIds) {
            View btn = dialog.findViewById(id);
            if (btn != null) btn.setOnClickListener(presetListener);
        }

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        if (btnTopUp != null && etAmount != null) {
            btnTopUp.setOnClickListener(v -> {
                String amountStr = etAmount.getText().toString().trim();

                if (amountStr.isEmpty()) {
                    Toast.makeText(getContext(), "Please enter an amount", Toast.LENGTH_SHORT).show();
                    return;
                }

                int amount;
                try {
                    amount = Integer.parseInt(amountStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(getContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (amount < MIN_TOP_UP_AMOUNT) {
                    Toast.makeText(getContext(), "Minimum Rp 30.000", Toast.LENGTH_SHORT).show();
                } else {
                    processTopUp(amount, dialog);
                }
            });
        }
        dialog.show();
    }

    private void processTopUp(int amountToAdd, BottomSheetDialog dialog) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        DocumentReference userDocRef = db.collection("users").document(user.getUid());

        userDocRef.get().addOnSuccessListener(snapshot -> {
            int currentBalance = 0;
            if (snapshot.exists()) {
                Number currentBalanceNum = (Number) snapshot.get("walletBalance");
                if (currentBalanceNum != null) currentBalance = currentBalanceNum.intValue();
            }

            int newBalance = currentBalance + amountToAdd;

            Map<String, Object> data = new HashMap<>();
            data.put("walletBalance", newBalance);

            userDocRef.set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Top Up Successful!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Top up failed", e);
                        Toast.makeText(getContext(), "Top Up Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            Log.d(TAG, "onStart: current user uid = " + user.getUid());
            startUserListener(user.getUid());
        }
    }

    private void startUserListener(String userId) {
        final DocumentReference userDocRef = db.collection("users").document(userId);

        userListener = userDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                Number balanceNum = (Number) snapshot.get("walletBalance");
                int balance = (balanceNum != null) ? balanceNum.intValue() : 0;

                if (tvBalance != null) {
                    tvBalance.setText(formatCurrency(balance));
                }

                Boolean isCardLinked = snapshot.getBoolean("isCardLinked");
                String last4 = snapshot.getString("cardLast4");

                if (Boolean.TRUE.equals(isCardLinked)) {
                    if (linkedCardView != null) linkedCardView.setVisibility(View.VISIBLE);
                    if (linkPaymentView != null) linkPaymentView.setVisibility(View.GONE);
                    if (tvCardLast4 != null) {
                        tvCardLast4.setText("•••• " + (last4 != null ? last4 : "xxxx"));
                    }
                } else {
                    if (linkedCardView != null) linkedCardView.setVisibility(View.GONE);
                    if (linkPaymentView != null) linkPaymentView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (userListener != null) {
            userListener.remove();
        }
    }

    private String formatCurrency(int amount) {
        Locale localeID = new Locale("in", "ID");
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(localeID);
        currencyFormatter.setMaximumFractionDigits(0);
        return currencyFormatter.format(amount);
    }
}