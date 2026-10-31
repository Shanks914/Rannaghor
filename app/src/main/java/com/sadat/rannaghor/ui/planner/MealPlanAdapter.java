package com.sadat.rannaghor.ui.planner;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.model.MealPlanEntry;

import java.util.ArrayList;
import java.util.List;

public class MealPlanAdapter extends RecyclerView.Adapter<MealPlanAdapter.VH> {

    public interface OnEntryClick {
        void onClick(MealPlanEntry entry);
    }

    private List<MealPlanEntry> items = new ArrayList<>();
    private final OnEntryClick listener;

    public MealPlanAdapter(OnEntryClick listener) {
        this.listener = listener;
    }

    public void submitList(List<MealPlanEntry> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_meal_plan_entry, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MealPlanEntry entry = items.get(position);
        holder.mealType.setText(entry.mealType);
        holder.recipeName.setText(entry.recipeName);
        holder.itemView.setOnClickListener(v -> listener.onClick(entry));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView mealType, recipeName;

        VH(@NonNull View itemView) {
            super(itemView);
            mealType = itemView.findViewById(R.id.textMealType);
            recipeName = itemView.findViewById(R.id.textPlannedRecipeName);
        }
    }
}
