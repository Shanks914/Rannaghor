package com.sadat.rannaghor.data.repository;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sadat.rannaghor.model.RatedRecipe;
import com.sadat.rannaghor.model.RecipeReview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.content.Context;

/** Public community reviews with one editable rating per authenticated user and recipe. */
public class RecipeReviewRepository {
    public interface ReviewsCallback {
        void onLoaded(List<RecipeReview> reviews, double average);
        void onError(Exception error);
    }
    public interface SaveCallback {
        void onComplete(@Nullable Exception error);
    }
    public interface PopularCallback {
        void onLoaded(List<RatedRecipe> recipes);
        void onError(Exception error);
    }
    public interface UserReviewCountsCallback {
        void onLoaded(int ratings, int writtenReviews);
        void onError(Exception error);
    }

    @Nullable private final FirebaseFirestore firestore;

    public RecipeReviewRepository(Context context) {
        FirebaseFirestore instance = null;
        if (!FirebaseApp.getApps(context.getApplicationContext()).isEmpty()) {
            try { instance = FirebaseFirestore.getInstance(); } catch (IllegalStateException ignored) { }
        }
        firestore = instance;
    }

    public void getReviews(String recipeId, ReviewsCallback callback) {
        if (firestore == null) { callback.onLoaded(new ArrayList<>(), 0); return; }
        firestore.collection("recipeReviews").whereEqualTo("recipeId", recipeId).get()
                .addOnSuccessListener(snapshot -> {
                    List<RecipeReview> reviews = parseReviews(snapshot.getDocuments());
                    callback.onLoaded(reviews, average(reviews));
                })
                .addOnFailureListener(callback::onError);
    }

    public void saveReview(String recipeId, String recipeName, String imageUrl, int rating, String text, SaveCallback callback) {
        if (firestore == null) { callback.onComplete(new IllegalStateException("Firebase is not configured.")); return; }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onComplete(new IllegalStateException("Sign in to rate a recipe."));
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("recipeId", recipeId);
        data.put("uid", user.getUid());
        data.put("recipeName", recipeName);
        data.put("imageUrl", imageUrl == null ? "" : imageUrl);
        data.put("rating", rating);
        data.put("review", text == null ? "" : text.trim());
        data.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection("recipeReviews").document(reviewDocumentId(recipeId, user.getUid())).set(data)
                .addOnSuccessListener(unused -> callback.onComplete(null))
                .addOnFailureListener(callback::onComplete);
    }

    public void getMostLoved(PopularCallback callback) {
        if (firestore == null) { callback.onLoaded(new ArrayList<>()); return; }
        firestore.collection("recipeReviews").get().addOnSuccessListener(snapshot -> {
            Map<String, List<DocumentSnapshot>> grouped = new HashMap<>();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                String recipeId = document.getString("recipeId");
                if (recipeId == null || recipeId.isEmpty()) continue;
                grouped.computeIfAbsent(recipeId, ignored -> new ArrayList<>()).add(document);
            }
            List<RatedRecipe> popular = new ArrayList<>();
            for (Map.Entry<String, List<DocumentSnapshot>> entry : grouped.entrySet()) {
                int total = 0;
                for (DocumentSnapshot review : entry.getValue()) total += review.getLong("rating") == null ? 0 : review.getLong("rating").intValue();
                if (entry.getValue().isEmpty()) continue;
                DocumentSnapshot first = entry.getValue().get(0);
                RatedRecipe recipe = new RatedRecipe();
                recipe.id = entry.getKey();
                recipe.name = first.getString("recipeName") == null ? "Recipe" : first.getString("recipeName");
                recipe.imageUrl = first.getString("imageUrl") == null ? "" : first.getString("imageUrl");
                recipe.reviewCount = entry.getValue().size();
                recipe.averageRating = (double) total / recipe.reviewCount;
                popular.add(recipe);
            }
            Collections.sort(popular, Comparator.comparingDouble((RatedRecipe recipe) -> recipe.averageRating)
                    .thenComparingInt(recipe -> recipe.reviewCount).reversed());
            if (popular.size() > 12) popular = new ArrayList<>(popular.subList(0, 12));
            callback.onLoaded(popular);
        }).addOnFailureListener(callback::onError);
    }

    /** Counts this account's ratings and the subset that include written text. */
    public void getUserReviewCounts(String userId, UserReviewCountsCallback callback) {
        if (firestore == null) { callback.onLoaded(0, 0); return; }
        firestore.collection("recipeReviews").whereEqualTo("uid", userId).get()
                .addOnSuccessListener(snapshot -> {
                    int written = 0;
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        String text = document.getString("review");
                        if (text != null && !text.trim().isEmpty()) written++;
                    }
                    callback.onLoaded(snapshot.size(), written);
                })
                .addOnFailureListener(callback::onError);
    }

    private List<RecipeReview> parseReviews(List<DocumentSnapshot> documents) {
        List<RecipeReview> reviews = new ArrayList<>();
        for (DocumentSnapshot document : documents) {
            RecipeReview review = new RecipeReview();
            review.userId = document.getString("uid") == null ? "" : document.getString("uid");
            Long rating = document.getLong("rating");
            review.rating = rating == null ? 0 : rating.intValue();
            review.review = document.getString("review") == null ? "" : document.getString("review");
            if (document.getTimestamp("updatedAt") != null) review.updatedAt = document.getTimestamp("updatedAt").toDate().getTime();
            if (review.rating >= 1 && review.rating <= 5) reviews.add(review);
        }
        reviews.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return reviews;
    }

    private String reviewDocumentId(String recipeId, String userId) {
        return recipeId.replaceAll("[^A-Za-z0-9_-]", "_") + "_" + userId;
    }

    private double average(List<RecipeReview> reviews) {
        if (reviews.isEmpty()) return 0;
        int total = 0;
        for (RecipeReview review : reviews) total += review.rating;
        return (double) total / reviews.size();
    }
}
