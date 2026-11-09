package com.sadat.rannaghor.ui.recipes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.model.RatedRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RatedRecipeAdapter extends RecyclerView.Adapter<RatedRecipeAdapter.Holder> {
    public interface OnClick { void onClick(RatedRecipe recipe); }
    private final OnClick listener;
    private List<RatedRecipe> items = new ArrayList<>();
    public RatedRecipeAdapter(OnClick listener) { this.listener = listener; }
    public void submitList(List<RatedRecipe> values) { items = values == null ? new ArrayList<>() : values; notifyDataSetChanged(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rated_recipe, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        RatedRecipe item = items.get(position);
        holder.name.setText(item.name);
        holder.rating.setText(String.format(Locale.getDefault(), "★ %.1f · %d reviews", item.averageRating, item.reviewCount));
        Glide.with(holder.image.getContext()).load(item.imageUrl).centerCrop().placeholder(R.drawable.bg_image_placeholder).into(holder.image);
        holder.itemView.setOnClickListener(view -> listener.onClick(item));
    }
    @Override public int getItemCount() { return items.size(); }
    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image; final TextView name, rating;
        Holder(View view) { super(view); image=view.findViewById(R.id.imageRatedRecipe); name=view.findViewById(R.id.textRatedRecipeName); rating=view.findViewById(R.id.textRatedRecipeRating); }
    }
}
