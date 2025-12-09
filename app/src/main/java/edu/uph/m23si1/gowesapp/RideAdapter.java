package edu.uph.m23si1.gowesapp;

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

public class RideAdapter extends RecyclerView.Adapter<RideAdapter.RideViewHolder> {

    private List<RideModel> rideList;

    public RideAdapter(List<RideModel> rideList) {
        this.rideList = rideList;
    }

    @NonNull
    @Override
    public RideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_ride, parent, false);
        return new RideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RideViewHolder holder, int position) {
        RideModel ride = rideList.get(position);

        // 1. Calculate Duration based on Cost (8000/hour)
        int cost = ride.getFinalCost();

        // Logic: Cost / 8000 = Hours.
        // Example: 16000 / 8000 = 2.0 hrs = 120 mins.
        double hours = (cost > 0) ? (double) cost / 8000.0 : 0;
        int totalMinutes = (int) Math.round(hours * 60);

        // Fallback: If calculation yields 0 but there is a stored duration, use it.
        // Otherwise ensure at least 1 min if user paid money.
        if (totalMinutes == 0 && ride.getDurationSeconds() > 0) {
            totalMinutes = (int) (ride.getDurationSeconds() / 60);
        } else if (totalMinutes == 0 && cost > 0) {
            totalMinutes = 1;
        }

        String durationText;
        if (totalMinutes >= 60) {
            int h = totalMinutes / 60;
            int m = totalMinutes % 60;
            if (m > 0) {
                durationText = String.format(Locale.getDefault(), "%d hr %d min", h, m);
            } else {
                durationText = String.format(Locale.getDefault(), "%d hr", h);
            }
        } else {
            durationText = String.format(Locale.getDefault(), "%d min", totalMinutes);
        }

        // 2. Generate BK Number (Strictly 001 - 004)
        // Math.abs handles rare negative timestamps
        long seed = Math.abs(ride.getTimestamp());
        int bikeNum = (int) (seed % 4) + 1; // % 4 returns 0,1,2,3. Add 1 -> 1,2,3,4.
        String bikeId = String.format(Locale.getDefault(), "BK-%03d", bikeNum);

        holder.tvBikeInfo.setText(bikeId + " • " + durationText);

        // 3. Format Date
        if (ride.getTimestamp() > 0) {
            Date date = new Date(ride.getTimestamp());
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
            holder.tvDate.setText(sdf.format(date));
        } else {
            holder.tvDate.setText("-");
        }

        // 4. Format Price
        holder.tvPrice.setText(formatCurrency(cost));
    }

    @Override
    public int getItemCount() {
        return rideList.size();
    }

    private String formatCurrency(int amount) {
        Locale localeID = new Locale("in", "ID");
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(localeID);
        currencyFormatter.setMaximumFractionDigits(0);
        return currencyFormatter.format(amount);
    }

    public static class RideViewHolder extends RecyclerView.ViewHolder {
        TextView tvBikeInfo, tvDate, tvPrice;

        public RideViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBikeInfo = itemView.findViewById(R.id.tv_bike_id_duration);
            tvDate = itemView.findViewById(R.id.tv_ride_date);
            tvPrice = itemView.findViewById(R.id.tv_ride_price);
        }
    }
}