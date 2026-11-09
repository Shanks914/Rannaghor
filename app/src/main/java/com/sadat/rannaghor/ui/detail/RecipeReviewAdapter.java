package com.sadat.rannaghor.ui.detail;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.model.RecipeReview;

import java.util.ArrayList;
import java.util.List;

public class RecipeReviewAdapter extends RecyclerView.Adapter<RecipeReviewAdapter.Holder> {
    private List<RecipeReview> items = new ArrayList<>();
    public void submitList(List<RecipeReview> values) { items = values == null ? new ArrayList<>() : values; notifyDataSetChanged(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recipe_review, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        RecipeReview review = items.get(position);
        StringBuilder stars = new StringBuilder();
        for (int i = 1; i <= 5; i++) stars.append(i <= review.rating ? '★' : '☆');
        holder.stars.setText(stars);
        holder.text.setText(review.review.isEmpty() ? "No written review." : review.review);
    }
    @Override public int getItemCount() { return items.size(); }
    static class Holder extends RecyclerView.ViewHolder {
        final TextView stars, text;
        Holder(View view) { super(view); stars=view.findViewById(R.id.textReviewStars); text=view.findViewById(R.id.textReviewBody); }
    }
}
