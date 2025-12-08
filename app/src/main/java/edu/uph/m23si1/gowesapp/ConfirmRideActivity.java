package edu.uph.m23si1.gowesapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ConfirmRideActivity extends AppCompatActivity {

    private static final String TAG = "ConfirmRideActivity";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration userListener;
    private String userId;

    private CardView payAsYouGoCard, useWalletCard;
    private ImageView radioPayAsYouGo, radioUseWallet;
    private LinearLayout linkCardPrompt, linkedCardDetails;
    private TextView tvWalletBalance, tvCardNumber; // Added tvCardNumber
    private Button btnConfirm, btnLinkCard;

    private boolean isWalletSelected = false;
    private int walletBalance = 0;
    private boolean isCardLinked = false;

    private static final int INITIAL_HOLD_AMOUNT = 25000;
    private static final int MIN_TOP_UP_AMOUNT = 30000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm_your_ride);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        payAsYouGoCard = findViewById(R.id.pay_as_you_go_card);
        useWalletCard = findViewById(R.id.use_wallet_card);
        radioPayAsYouGo = findViewById(R.id.radio_btn_payg);
        radioUseWallet = findViewById(R.id.radio_btn_wallet);
        linkCardPrompt = findViewById(R.id.link_card_prompt);
        linkedCardDetails = findViewById(R.id.linked_card_details);
        tvWalletBalance = findViewById(R.id.tv_wallet_balance);
        tvCardNumber = findViewById(R.id.tv_card_number); // Init tvCardNumber
        btnConfirm = findViewById(R.id.btn_confirm);
        btnLinkCard = findViewById(R.id.btn_link_card);

        // Back Button Logic
        ImageView ivBack = findViewById(R.id.iv_back);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }

        // 🔎 Log the bike passed from previous screen (optional, but useful)
        String bikeId = getIntent().getStringExtra("BIKE_ID");
        Log.d(TAG, "ConfirmRideActivity started with BIKE_ID = " + bikeId);

        // Click listeners
        if (payAsYouGoCard != null) {
            payAsYouGoCard.setOnClickListener(v -> {
                isWalletSelected = false;
                updatePaymentSelection();
            });
        }

        if (useWalletCard != null) {
            useWalletCard.setOnClickListener(v -> {
                isWalletSelected = true;
                updatePaymentSelection();
            });
        }

        if (btnLinkCard != null) {
            btnLinkCard.setOnClickListener(v -> {
                showLinkPaymentDialog();
            });
        }

        // Button always responsive, logic inside
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                if (isWalletSelected) {
                    if (walletBalance < INITIAL_HOLD_AMOUNT) {
                        showInsufficientBalanceDialog();
                    } else {
                        startRide();
                    }
                } else {
                    // Card path
                    if (isCardLinked) {
                        startRide();
                    } else {
                        Toast.makeText(this, "Please link a payment card first", Toast.LENGTH_SHORT).show();
                        showLinkPaymentDialog();
                    }
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            userId = user.getUid();
            startUserListener(userId);
        } else {
            Log.w(TAG, "No logged-in user, finishing ConfirmRideActivity");
            finish();
        }
    }

    private void startUserListener(String userId) {
        final DocumentReference userDocRef = db.collection("users").document(userId);

        userListener = userDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) return;
            if (snapshot != null && snapshot.exists()) {
                Number balanceNum = (Number) snapshot.get("walletBalance");
                Boolean cardLinked = snapshot.getBoolean("isCardLinked");
                String last4 = snapshot.getString("cardLast4");

                walletBalance = (balanceNum != null) ? balanceNum.intValue() : 0;
                isCardLinked = (cardLinked != null) ? cardLinked : false;

                // Update card text
                if (tvCardNumber != null) {
                    tvCardNumber.setText("•••• " + (last4 != null ? last4 : "xxxx"));
                }

                updateUI();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userListener != null) {
            userListener.remove();
        }
    }

    private void updateUI() {
        if (tvWalletBalance != null) {
            tvWalletBalance.setText("Current balance: " + formatCurrency(walletBalance));
        }

        if (isCardLinked) {
            if (payAsYouGoCard != null) payAsYouGoCard.setVisibility(View.VISIBLE);
            if (linkedCardDetails != null) linkedCardDetails.setVisibility(View.VISIBLE);
            if (linkCardPrompt != null) linkCardPrompt.setVisibility(View.GONE);
        } else {
            if (payAsYouGoCard != null) payAsYouGoCard.setVisibility(View.GONE);
            isWalletSelected = true;
        }

        updatePaymentSelection();
    }

    private void updatePaymentSelection() {
        if (isWalletSelected) {
            if (useWalletCard != null) {
                useWalletCard.setBackgroundResource(R.drawable.card_payment_selected);
            }
            if (radioUseWallet != null) {
                radioUseWallet.setImageResource(R.drawable.ic_radio_button_checked);
            }

            if (payAsYouGoCard != null && payAsYouGoCard.getVisibility() == View.VISIBLE) {
                payAsYouGoCard.setBackgroundResource(R.drawable.card_payment_unselected);
            }
            if (radioPayAsYouGo != null) {
                radioPayAsYouGo.setImageResource(R.drawable.ic_radio_button_unchecked);
            }
        } else {
            if (payAsYouGoCard != null) {
                payAsYouGoCard.setBackgroundResource(R.drawable.card_payment_selected);
            }
            if (radioPayAsYouGo != null) {
                radioPayAsYouGo.setImageResource(R.drawable.ic_radio_button_checked);
            }

            if (useWalletCard != null) {
                useWalletCard.setBackgroundResource(R.drawable.card_payment_unselected);
            }
            if (radioUseWallet != null) {
                radioUseWallet.setImageResource(R.drawable.ic_radio_button_unchecked);
            }
        }

        if (btnConfirm != null) {
            btnConfirm.setEnabled(true);
            btnConfirm.setAlpha(1.0f);
        }
    }

    private void startRide() {
        if (userId != null) {
            db.collection("users").document(userId).update("isActiveRide", true);
        }
        Intent intent = new Intent(ConfirmRideActivity.this, ActiveRideActivity.class);
        // Pass payment method if needed
        intent.putExtra("PAYMENT_METHOD", isWalletSelected ? "Wallet" : "Card");
        startActivity(intent);
        finish();
    }

    // --- DIALOGS ---

    private void showInsufficientBalanceDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_insufficient_balance);

        Button topUpNow = dialog.findViewById(R.id.btn_top_up_now);
        Button maybeLater = dialog.findViewById(R.id.btn_maybe_later);

        if (topUpNow != null) topUpNow.setOnClickListener(v -> {
            dialog.dismiss();
            showTopUpDialog();
        });

        if (maybeLater != null) maybeLater.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showLinkPaymentDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
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
                    Toast.makeText(this, "Invalid card number", Toast.LENGTH_SHORT).show();
                    return;
                }

                String last4 = cardNumber.substring(cardNumber.length() - 4);

                if (userId != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("isCardLinked", true);
                    data.put("cardLast4", last4);

                    db.collection("users").document(userId)
                            .set(data, SetOptions.merge())
                            .addOnSuccessListener(a -> {
                                Toast.makeText(this, "Card Linked Successfully", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            });
                }
            });
        }
        dialog.show();
    }

    private void showTopUpDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_top_up_balance);

        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnTopUp = dialog.findViewById(R.id.btn_top_up);
        EditText etAmount = dialog.findViewById(R.id.et_amount);

        Button btn30k = dialog.findViewById(R.id.btn_amount_30k);
        Button btn50k = dialog.findViewById(R.id.btn_amount_50k);
        Button btn100k = dialog.findViewById(R.id.btn_amount_100k);
        Button btn200k = dialog.findViewById(R.id.btn_amount_200k);

        View.OnClickListener presetListener = v -> {
            Button b = (Button) v;
            String text = b.getText().toString();
            String amountStr = text.replaceAll("[^0-9]", "");
            if (etAmount != null) {
                etAmount.setText(amountStr);
                etAmount.setSelection(etAmount.getText().length());
            }
        };

        if (btn30k != null) btn30k.setOnClickListener(presetListener);
        if (btn50k != null) btn50k.setOnClickListener(presetListener);
        if (btn100k != null) btn100k.setOnClickListener(presetListener);
        if (btn200k != null) btn200k.setOnClickListener(presetListener);

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        if (btnTopUp != null) {
            btnTopUp.setOnClickListener(v -> {
                if (etAmount == null) return;
                String amountStr = etAmount.getText().toString();
                if (amountStr.isEmpty()) {
                    Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show();
                    return;
                }

                int amount;
                try {
                    amount = Integer.parseInt(amountStr);
                } catch (NumberFormatException e) {
                    return;
                }

                if (amount < MIN_TOP_UP_AMOUNT) {
                    Toast.makeText(this, "Minimum top-up is " + formatCurrency(MIN_TOP_UP_AMOUNT), Toast.LENGTH_SHORT).show();
                } else {
                    if (userId != null) {
                        DocumentReference userDocRef = db.collection("users").document(userId);
                        db.runTransaction(transaction -> {
                            DocumentSnapshot snapshot = transaction.get(userDocRef);
                            Number currentBalanceNum = (Number) snapshot.get("walletBalance");
                            int currentBalance = (currentBalanceNum != null) ? currentBalanceNum.intValue() : 0;
                            int newBalance = currentBalance + amount;
                            transaction.update(userDocRef, "walletBalance", newBalance);
                            return newBalance;
                        }).addOnSuccessListener(newBalance -> {
                            Toast.makeText(this, "Top Up Successful!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        });
                    }
                }
            });
        }
        dialog.show();
    }

    private String formatCurrency(int amount) {
        Locale localeID = new Locale("in", "ID");
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(localeID);
        currencyFormatter.setMaximumFractionDigits(0);
        return currencyFormatter.format(amount);
    }
}