package com.sadat.rannaghor.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.model.ShoppingItem;

import java.util.List;

/**
 * Exposes the shopping list so another app (or another part of this app
 * using a ContentResolver instead of Room directly) can read it.
 * Read-only on purpose - the app itself still writes through Room/ShoppingDao.
 *
 * Query it like this from elsewhere in the app:
 *   Uri uri = Uri.parse("content://com.sadat.rannaghor.provider.shopping/items");
 *   Cursor cursor = getContentResolver().query(uri, null, null, null, null);
 */
public class ShoppingListProvider extends ContentProvider {

    public static final String AUTHORITY = "com.sadat.rannaghor.provider.shopping";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/items");

    private static final int ITEMS = 1;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, "items", ITEMS);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                         @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (MATCHER.match(uri) != ITEMS) return null;

        MatrixCursor cursor = new MatrixCursor(new String[]{"_id", "name", "quantity", "isChecked"});
        List<ShoppingItem> items = AppDatabase.getInstance(getContext()).shoppingDao().getAll();
        for (ShoppingItem item : items) {
            cursor.addRow(new Object[]{item.id, item.name, item.quantity, item.isChecked ? 1 : 0});
        }
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".items";
    }

    // Writes go through Room (ShoppingDao) in this starter project, so these
    // are left unimplemented. Fill them in only if an outside app needs to write too.
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("Insert through ShoppingDao, not this provider.");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Delete through ShoppingDao, not this provider.");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("Update through ShoppingDao, not this provider.");
    }
}
