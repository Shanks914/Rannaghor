package com.sadat.rannaghor.ui.category;

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

/** Two-column photo cards for recipe collection pages. */
public class RecipeCategoryAdapter extends RecyclerView.Adapter<RecipeCategoryAdapter.Holder> {
    public interface OnRecipeClick { void onClick(Recipe recipe); }
    private final OnRecipeClick listener;
    private List<Recipe> recipes = new ArrayList<>();

    public RecipeCategoryAdapter(OnRecipeClick listener) { this.listener = listener; }
    public void submitList(List<Recipe> values) {
        recipes = values == null ? new ArrayList<>() : values;
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_recipe, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Recipe recipe = recipes.get(position);
        holder.name.setText(recipe.name);
        Object image = recipe.imageUrl;
        if (recipe.imageUrl == null || recipe.imageUrl.trim().isEmpty()) {
            if ("local_1".equals(recipe.id)) image = R.drawable.home_khichuri;
            else if ("local_2".equals(recipe.id)) image = R.drawable.category_vegetarian;
            else if ("local_3".equals(recipe.id)) image = R.drawable.home_chicken;
        }
        Glide.with(holder.image.getContext()).load(image)
                .centerCrop().placeholder(R.drawable.bg_image_placeholder).into(holder.image);
        holder.itemView.setOnClickListener(v -> listener.onClick(recipe));
    }
    @Override public int getItemCount() { return recipes.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name;
        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.imageCategoryRecipe);
            name = view.findViewById(R.id.textCategoryRecipeName);
        }
    }
}
