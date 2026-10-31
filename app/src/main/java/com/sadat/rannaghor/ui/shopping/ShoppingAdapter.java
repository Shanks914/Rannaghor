package com.sadat.rannaghor.ui.shopping;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.model.ShoppingItem;

import java.util.ArrayList;
import java.util.List;

public class ShoppingAdapter extends RecyclerView.Adapter<ShoppingAdapter.VH> {

    public interface OnToggle {
        void onToggle(ShoppingItem item, boolean checked);
    }

    private List<ShoppingItem> items = new ArrayList<>();
    private final OnToggle listener;

    public ShoppingAdapter(OnToggle listener) {
        this.listener = listener;
    }

    public void submitList(List<ShoppingItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shopping, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ShoppingItem item = items.get(position);
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(item.isChecked);
        holder.name.setText(item.quantity == null || item.quantity.isEmpty()
                ? item.name
                : item.name + " (" + item.quantity + ")");
        holder.checkBox.setOnCheckedChangeListener((btn, checked) -> listener.onToggle(item, checked));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView name;

        VH(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkShoppingItem);
            name = itemView.findViewById(R.id.textShoppingItemName);
        }
    }
}
