package com.sadat.rannaghor.ui.planner;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.model.MealPlanEntry;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MealPlanAdapter extends RecyclerView.Adapter<MealPlanAdapter.VH> {
    public interface OnEntryClick { void onClick(MealPlanEntry entry); }
    private final OnEntryClick openListener;
    private final OnEntryClick deleteListener;
    private List<Row> rows = new ArrayList<>();

    public MealPlanAdapter(OnEntryClick openListener, OnEntryClick deleteListener) {
        this.openListener = openListener;
        this.deleteListener = deleteListener;
    }

    public void submitList(List<MealPlanEntry> list) {
        rows = new ArrayList<>();
        String previousSection = null;
        if (list != null) for (MealPlanEntry entry : list) {
            String section = entry.customMealName == null || entry.customMealName.trim().isEmpty()
                    ? entry.mealType : entry.customMealName.trim();
            if (section == null || section.trim().isEmpty()) section = "Meals";
            if (!section.equals(previousSection)) {
                rows.add(new Row(section, null));
                previousSection = section;
            }
            rows.add(new Row(null, entry));
        }
        notifyDataSetChanged();
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == 0 ? R.layout.item_meal_section_header : R.layout.item_meal_plan_entry;
        return new VH(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false), viewType);
    }

    @Override public void onBindViewHolder(@NonNull VH holder, int position) {
        Row row = rows.get(position);
        if (row.entry == null) {
            holder.section.setText(row.section);
            return;
        }
        MealPlanEntry entry = row.entry;
        String label = entry.customMealName == null || entry.customMealName.trim().isEmpty()
                ? entry.mealType : entry.customMealName;
        holder.mealType.setText(label == null ? "Meal" : label);
        holder.recipeName.setText(entry.recipeName);
        holder.time.setText(entry.scheduledAtMillis > 0
                ? new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(entry.scheduledAtMillis))
                : "Time not set");
        String status = entry.status == null ? "PLANNED" : entry.status;
        if ("COMPLETED".equals(status)) {
            holder.status.setText("Completed");
            holder.status.setVisibility(View.VISIBLE);
        } else if ("DISMISSED".equals(status)) {
            holder.status.setText("Dismissed");
            holder.status.setVisibility(View.VISIBLE);
        } else if ("IN_PROGRESS".equals(status)) {
            holder.status.setText("Cooking now");
            holder.status.setVisibility(View.VISIBLE);
        } else {
            holder.status.setVisibility(View.GONE);
        }
        Glide.with(holder.image.getContext()).load(entry.imageUrl)
                .centerCrop().placeholder(R.drawable.bg_image_placeholder).into(holder.image);
        holder.itemView.setOnClickListener(v -> openListener.onClick(entry));
        holder.delete.setOnClickListener(v -> deleteListener.onClick(entry));
    }

    @Override public int getItemViewType(int position) { return rows.get(position).entry == null ? 0 : 1; }
    @Override public int getItemCount() { return rows.size(); }

    private static class Row {
        final String section;
        final MealPlanEntry entry;
        Row(String section, MealPlanEntry entry) { this.section = section; this.entry = entry; }
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView mealType, recipeName, time, status, delete;
        final TextView section;
        VH(@NonNull View itemView, int viewType) {
            super(itemView);
            section = viewType == 0 ? itemView.findViewById(R.id.textMealSectionName) : null;
            if (viewType == 0) {
                image = null; mealType = null; recipeName = null; time = null; status = null; delete = null;
                return;
            }
            image = itemView.findViewById(R.id.imagePlannedRecipe);
            mealType = itemView.findViewById(R.id.textMealType);
            recipeName = itemView.findViewById(R.id.textPlannedRecipeName);
            time = itemView.findViewById(R.id.textPlannedTime);
            status = itemView.findViewById(R.id.textPlanStatus);
            delete = itemView.findViewById(R.id.buttonDeletePlannedMeal);
        }
    }
}
