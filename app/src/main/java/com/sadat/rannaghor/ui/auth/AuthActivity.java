package com.sadat.rannaghor.ui.auth;

import android.app.Activity;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.CloudBackupManager;

/** Sign-in gate used before a user can publish recipe ratings and reviews. */
public class AuthActivity extends AppCompatActivity {
    private EditText email, password;
    private TextView login, create;
    private FirebaseAuth auth;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        findViewById(R.id.buttonAuthBack).setOnClickListener(v -> finish());
        email = findViewById(R.id.editAuthEmail);
        password = findViewById(R.id.editAuthPassword);
        login = findViewById(R.id.buttonAuthLogin);
        create = findViewById(R.id.buttonAuthCreate);
        login.setOnClickListener(v -> authenticate(false));
        create.setOnClickListener(v -> authenticate(true));
        if (CloudBackupManager.isConfigured(this)) auth = FirebaseAuth.getInstance();
        else Toast.makeText(this, R.string.firebase_setup_needed, Toast.LENGTH_LONG).show();
    }

    private void authenticate(boolean createAccount) {
        if (auth == null) return;
        String address = email.getText().toString().trim();
        String secret = password.getText().toString();
        if (!Patterns.EMAIL_ADDRESS.matcher(address).matches()) { email.setError("Enter a valid email address"); return; }
        if (secret.length() < 6) { password.setError("Use at least 6 characters"); return; }
        login.setEnabled(false);
        create.setEnabled(false);
        if (createAccount) {
            auth.createUserWithEmailAndPassword(address, secret).addOnCompleteListener(this, task -> complete(task.isSuccessful(), task.getException()));
        } else {
            auth.signInWithEmailAndPassword(address, secret).addOnCompleteListener(this, task -> complete(task.isSuccessful(), task.getException()));
        }
    }

    private void complete(boolean success, Exception error) {
        login.setEnabled(true);
        create.setEnabled(true);
        if (!success) {
            Toast.makeText(this, error == null || error.getLocalizedMessage() == null ? "Could not sign in. Check your details." : error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        CloudBackupManager.restoreAndBackup(this, ignored -> { });
        setResult(Activity.RESULT_OK);
        finish();
    }
}
