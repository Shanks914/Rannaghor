package com.sadat.rannaghor.ui.auth;

import android.app.Activity;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.core.content.ContextCompat;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

/** Shared Credential Manager flow for Google sign-in and sensitive-action reauthentication. */
public final class GoogleSignInHelper {
    public interface Callback {
        void onCredential(AuthCredential credential);
        void onError(Exception error);
    }

    private GoogleSignInHelper() { }

    public static void requestCredential(Activity activity, Callback callback) {
        int id = activity.getResources().getIdentifier("default_web_client_id", "string", activity.getPackageName());
        if (id == 0) {
            callback.onError(new IllegalStateException("Google sign-in needs the Web client ID. Enable Google in Firebase Authentication, add this app’s SHA fingerprints, then replace app/google-services.json."));
            return;
        }
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(activity.getString(id))
                .setAutoSelectEnabled(false)
                .build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();
        CredentialManager.create(activity).getCredentialAsync(activity, request, null, ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (!(credential instanceof CustomCredential)
                                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            callback.onError(new IllegalStateException("Google returned an unsupported sign-in credential."));
                            return;
                        }
                        try {
                            GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(credential.getData());
                            callback.onCredential(GoogleAuthProvider.getCredential(google.getIdToken(), null));
                        } catch (Exception error) {
                            callback.onError(error);
                        }
                    }

                    @Override public void onError(GetCredentialException error) {
                        callback.onError(error);
                    }
                });
    }
}
