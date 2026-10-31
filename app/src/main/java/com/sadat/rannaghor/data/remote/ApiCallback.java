package com.sadat.rannaghor.data.remote;

import java.util.List;

import com.sadat.rannaghor.model.Recipe;

// Simple callback so network results can be delivered back to the UI thread.
public interface ApiCallback {
    void onSuccess(List<Recipe> recipes);
    void onError(String message);
}

