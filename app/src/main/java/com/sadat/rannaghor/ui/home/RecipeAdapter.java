package com.sadat.rannaghor.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.model.Recipe;

import java.util.ArrayList;
import java.util.List;

/** Shows recipe cards in a RecyclerView - used on Home, Favorites and search results. */
public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder> {

    public interface OnRecipeClick {
        void onClick(Recipe recipe);
    }

    private List<Recipe> items = new ArrayList<>();
    private final OnRecipeClick listener;

    public RecipeAdapter(OnRecipeClick listener) {
        this.listener = listener;
    }

    public void submitList(List<Recipe> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recipe_card, parent, false);
        return new RecipeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
        Recipe recipe = items.get(position);
        holder.name.setText(recipe.name);

        if (recipe.totalIngredientCount > 0) {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(recipe.matchedIngredientCount + "/" + recipe.totalIngredientCount + " matched");
        } else {
            holder.badge.setVisibility(View.GONE);
        }

        Glide.with(holder.image.getContext())
                .load(recipe.imageUrl)
                .centerCrop()
                .placeholder(R.drawable.bg_image_placeholder)
                .into(holder.image);

        holder.itemView.setOnClickListener(v -> listener.onClick(recipe));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class RecipeViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        TextView name;
        TextView badge;

        RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imageRecipe);
            name = itemView.findViewById(R.id.textRecipeName);
            badge = itemView.findViewById(R.id.textMatchBadge);
        }
    }
}
