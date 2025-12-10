package edu.uph.m23si1.gowesapp;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<Transaction> transactionList;

    public TransactionAdapter(List<Transaction> transactionList) {
        this.transactionList = transactionList;
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // NOTE: Ensure you have a layout file named item_transaction.xml
        // with ids: tv_txn_desc, tv_txn_date, tv_txn_amount
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction txn = transactionList.get(position);
        holder.bind(txn);
    }

    @Override
    public int getItemCount() {
        return transactionList.size();
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        TextView tvDesc, tvDate, tvAmount;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDesc = itemView.findViewById(R.id.tv_txn_desc);
            tvDate = itemView.findViewById(R.id.tv_txn_date);
            tvAmount = itemView.findViewById(R.id.tv_txn_amount);
        }

        public void bind(Transaction txn) {
            if (tvDesc != null) tvDesc.setText(txn.getDescription());

            if (tvDate != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
                tvDate.setText(sdf.format(new Date(txn.getTimestamp())));
            }

            if (tvAmount != null) {
                Locale localeID = new Locale("in", "ID");
                NumberFormat format = NumberFormat.getCurrencyInstance(localeID);
                format.setMaximumFractionDigits(0);

                String amountStr = format.format(txn.getAmount());
                tvAmount.setText(amountStr);

                // Green for TopUp/Refund, Black/Red for Payment
                if (txn.getAmount() > 0) {
                    tvAmount.setTextColor(Color.parseColor("#4CAF50")); // Green
                    tvAmount.setText("+ " + amountStr);
                } else {
                    tvAmount.setTextColor(Color.BLACK);
                }
            }
        }
    }
}