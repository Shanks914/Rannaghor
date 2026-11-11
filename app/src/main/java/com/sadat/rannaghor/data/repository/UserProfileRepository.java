package com.sadat.rannaghor.data.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Stores a small account-owned avatar in Firestore so it follows the user across devices. */
public final class UserProfileRepository {
    public interface AvatarCallback { void onLoaded(String base64); }
    public interface SaveCallback { void onComplete(Exception error); }

    private UserProfileRepository() { }

    public static String compressAvatar(Context context, Uri uri) throws IOException {
        Bitmap source;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Could not open the selected image.");
            source = BitmapFactory.decodeStream(input);
        }
        if (source == null) throw new IOException("The selected file is not a supported image.");
        int width = source.getWidth();
        int height = source.getHeight();
        float ratio = Math.min(1f, 256f / Math.max(width, height));
        int targetWidth = Math.max(1, Math.round(width * ratio));
        int targetHeight = Math.max(1, Math.round(height * ratio));
        Bitmap scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
        if (scaled != source) source.recycle();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 78, output);
        scaled.recycle();
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    public static void loadAvatar(String uid, AvatarCallback callback) {
        profileDocument(uid).get().addOnSuccessListener(snapshot -> {
            String encoded = snapshot.getString("avatarBase64");
            callback.onLoaded(encoded == null ? "" : encoded);
        }).addOnFailureListener(error -> callback.onLoaded(""));
    }

    public static void saveAvatar(String uid, String base64, SaveCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("avatarBase64", base64);
        data.put("updatedAt", FieldValue.serverTimestamp());
        profileDocument(uid).set(data).addOnSuccessListener(unused -> callback.onComplete(null))
                .addOnFailureListener(callback::onComplete);
    }

    public static byte[] decodeAvatar(String base64) {
        try { return Base64.getDecoder().decode(base64); }
        catch (IllegalArgumentException error) { return null; }
    }

    private static com.google.firebase.firestore.DocumentReference profileDocument(String uid) {
        return FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("profile").document("details");
    }
}
