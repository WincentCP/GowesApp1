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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WalletFragment extends Fragment {

    private static final String TAG = "WalletFragment";
    private static final int MIN_TOP_UP_AMOUNT = 30000;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration userListener;
    private ListenerRegistration transactionsListener;

    private TextView tvBalance, tvCardLast4;
    private CardView linkedCardView, linkPaymentView;
    private ImageView btnRemoveCard;

    private RecyclerView rvTransactions;
    private TransactionAdapter transactionAdapter;
    private List<Transaction> transactionList;

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
        rvTransactions = view.findViewById(R.id.rv_transactions);

        if (rvTransactions != null) {
            rvTransactions.setLayoutManager(new LinearLayoutManager(getContext()));
            transactionList = new ArrayList<>();
            transactionAdapter = new TransactionAdapter(transactionList);
            rvTransactions.setAdapter(transactionAdapter);
        }

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
            data.put("cardLast4", null);

            db.collection("users").document(user.getUid())
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Card Removed", Toast.LENGTH_SHORT).show());
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
                if (etCardNumber != null) cardNumber = etCardNumber.getText().toString().replace(" ", "");

                if (cardNumber.length() < 13) {
                    Toast.makeText(getContext(), "Invalid card number", Toast.LENGTH_SHORT).show();
                    return;
                }
                String last4 = cardNumber.substring(cardNumber.length() - 4);

                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("isCardLinked", true);
                    data.put("cardLast4", last4);

                    db.collection("users").document(user.getUid())
                            .set(data, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(), "Card Linked Successfully!", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
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

        View.OnClickListener presetListener = v -> {
            if (etAmount != null && v instanceof Button) {
                String text = ((Button) v).getText().toString();
                String cleanAmount = text.replaceAll("[^0-9]", "");
                etAmount.setText(cleanAmount);
                etAmount.setSelection(etAmount.getText().length());
            }
        };

        int[] presetIds = {R.id.btn_amount_30k, R.id.btn_amount_50k, R.id.btn_amount_100k, R.id.btn_amount_200k};
        for (int id : presetIds) {
            View btn = dialog.findViewById(id);
            if (btn != null) btn.setOnClickListener(presetListener);
        }

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        if (btnTopUp != null && etAmount != null) {
            btnTopUp.setOnClickListener(v -> {
                String amountStr = etAmount.getText().toString().trim();
                if (amountStr.isEmpty()) return;
                int amount = Integer.parseInt(amountStr);
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
        userDocRef.update("walletBalance", FieldValue.increment(amountToAdd))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Top Up Successful!", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();

                    Map<String, Object> txn = new HashMap<>();
                    txn.put("amount", amountToAdd);
                    txn.put("description", "Top Up Wallet");
                    txn.put("type", "TopUp");
                    txn.put("status", "Success");
                    txn.put("timestamp", System.currentTimeMillis());

                    userDocRef.collection("transactions").add(txn);
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            startUserListener(user.getUid());
            startTransactionsListener(user.getUid());
        }
    }

    private void startUserListener(String userId) {
        final DocumentReference userDocRef = db.collection("users").document(userId);
        userListener = userDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null || snapshot == null || !snapshot.exists()) return;

            Number balanceNum = (Number) snapshot.get("walletBalance");
            int balance = (balanceNum != null) ? balanceNum.intValue() : 0;
            if (tvBalance != null) tvBalance.setText(formatCurrency(balance));

            Boolean isCardLinked = snapshot.getBoolean("isCardLinked");
            String last4 = snapshot.getString("cardLast4");

            if (Boolean.TRUE.equals(isCardLinked)) {
                if (linkedCardView != null) linkedCardView.setVisibility(View.VISIBLE);
                if (linkPaymentView != null) linkPaymentView.setVisibility(View.GONE);
                if (tvCardLast4 != null) tvCardLast4.setText("•••• " + (last4 != null ? last4 : "xxxx"));
            } else {
                if (linkedCardView != null) linkedCardView.setVisibility(View.GONE);
                if (linkPaymentView != null) linkPaymentView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void startTransactionsListener(String userId) {
        if (rvTransactions == null) return;

        transactionsListener = db.collection("users").document(userId)
                .collection("transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    if (snapshots != null) {
                        transactionList.clear();
                        for (QueryDocumentSnapshot doc : snapshots) {
                            Transaction txn = doc.toObject(Transaction.class);
                            transactionList.add(txn);
                        }
                        transactionAdapter.notifyDataSetChanged();
                    }
                });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (userListener != null) userListener.remove();
        if (transactionsListener != null) transactionsListener.remove();
    }

    private String formatCurrency(int amount) {
        Locale localeID = new Locale("in", "ID");
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(localeID);
        currencyFormatter.setMaximumFractionDigits(0);
        return currencyFormatter.format(amount);
    }
}