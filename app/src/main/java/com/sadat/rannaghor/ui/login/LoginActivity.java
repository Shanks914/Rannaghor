package com.sadat.rannaghor.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.CloudBackupManager;
import com.sadat.rannaghor.ui.home.HomeActivity;

/** Email/password Firebase login, with an offline-friendly skip option. */
public class LoginActivity extends AppCompatActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private FirebaseAuth auth;
    private boolean firebaseConfigured;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.editEmail);
        passwordInput = findViewById(R.id.editPassword);
        Button loginButton = findViewById(R.id.buttonLogin);
        Button createAccountButton = findViewById(R.id.buttonCreateAccount);
        TextView skip = findViewById(R.id.textSkip);

        firebaseConfigured = CloudBackupManager.isConfigured(this);
        if (firebaseConfigured) {
            auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) {
                finishAuthentication();
            }
        }

        loginButton.setOnClickListener(v -> authenticate(false));
        createAccountButton.setOnClickListener(v -> authenticate(true));
        skip.setOnClickListener(v -> goHome());
    }

    private void authenticate(boolean createAccount) {
        if (!firebaseConfigured || auth == null) {
            Toast.makeText(this, R.string.firebase_setup_needed, Toast.LENGTH_LONG).show();
            return;
        }

        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email address");
            return;
        }
        if (password.length() < 6) {
            passwordInput.setError("Use at least 6 characters");
            return;
        }

        loginButtonsEnabled(false);
        if (createAccount) {
            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        loginButtonsEnabled(true);
                        if (task.isSuccessful() && task.getResult().getUser() != null) {
                            finishAuthentication();
                        } else {
                            showAuthError(R.string.firebase_signup_failed, task.getException());
                        }
                    });
        } else {
            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        loginButtonsEnabled(true);
                        if (task.isSuccessful() && task.getResult().getUser() != null) {
                            finishAuthentication();
                        } else {
                            showAuthError(R.string.firebase_login_failed, task.getException());
                        }
                    });
        }
    }

    private void finishAuthentication() {
        CloudBackupManager.restoreAndBackup(this, error -> {
            if (isFinishing()) return;
            if (error != null) {
                String reason = error.getLocalizedMessage() == null
                        ? "Check your internet connection and Firestore rules."
                        : error.getLocalizedMessage();
                Toast.makeText(this, getString(R.string.cloud_sync_failed, reason), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.cloud_sync_succeeded, Toast.LENGTH_SHORT).show();
            }
            goHome();
        });
    }

    private void showAuthError(int messageId, Exception error) {
        String details = error == null || error.getLocalizedMessage() == null
                ? "Please check your details and try again."
                : error.getLocalizedMessage();
        Toast.makeText(this, getString(messageId, details), Toast.LENGTH_LONG).show();
    }

    private void loginButtonsEnabled(boolean enabled) {
        findViewById(R.id.buttonLogin).setEnabled(enabled);
        findViewById(R.id.buttonCreateAccount).setEnabled(enabled);
    }

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
