package com.sadat.rannaghor.ui.shopping;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.model.ShoppingItem;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shopping list. In this starter version you add items by hand; in the
 * fuller build, "Generate from this week's plan" would loop over the
 * week's MealPlanEntry -> Recipe ingredients and merge duplicates
 * before inserting them here.
 */
public class ShoppingListActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ShoppingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping_list);

        findViewById(R.id.buttonBack).setOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recyclerShopping);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ShoppingAdapter(this::onToggleItem);
        recyclerView.setAdapter(adapter);

        EditText itemInput = findViewById(R.id.editShoppingItem);
        findViewById(R.id.buttonAddItem).setOnClickListener(v -> {
            String text = itemInput.getText().toString().trim();
            if (!text.isEmpty()) {
                addItem(text);
                itemInput.setText("");
            }
        });

        findViewById(R.id.buttonShareList).setOnClickListener(v -> shareList());

        loadItems();
    }

    private void addItem(String name) {
        ShoppingItem item = new ShoppingItem();
        item.name = name;
        item.quantity = "";
        item.isChecked = false;
        executor.execute(() -> {
            AppDatabase.getInstance(this).shoppingDao().insert(item);
            runOnUiThread(this::loadItems);
        });
    }

    private void onToggleItem(ShoppingItem item, boolean checked) {
        item.isChecked = checked;
        executor.execute(() -> AppDatabase.getInstance(this).shoppingDao().update(item));
    }

    private void loadItems() {
        executor.execute(() -> {
            List<ShoppingItem> items = AppDatabase.getInstance(this).shoppingDao().getAll();
            runOnUiThread(() -> adapter.submitList(items));
        });
    }

    /** Builds a plain-text list and hands it to any app the user picks (ACTION_SEND). */
    private void shareList() {
        executor.execute(() -> {
            List<ShoppingItem> items = AppDatabase.getInstance(this).shoppingDao().getAll();
            StringBuilder sb = new StringBuilder("Shopping list (Rannaghor):\n");
            for (ShoppingItem item : items) {
                sb.append(item.isChecked ? "[x] " : "[ ] ").append(item.name);
                if (item.quantity != null && !item.quantity.isEmpty()) {
                    sb.append(" (").append(item.quantity).append(")");
                }
                sb.append("\n");
            }
            runOnUiThread(() -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
                startActivity(Intent.createChooser(shareIntent, "Share shopping list"));
            });
        });
    }
}
