package com.sadat.rannaghor.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.sadat.rannaghor.R;
import com.sadat.rannaghor.ui.home.HomeActivity;

/**
 * Entry screen. Right now this just skips straight to Home so you have
 * something running immediately - wire up Firebase Authentication here
 * once you've finished the Firebase console setup (see README "Step 8").
 */
public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Button loginButton = findViewById(R.id.buttonLogin);
        TextView skip = findViewById(R.id.textSkip);

        loginButton.setOnClickListener(v -> {
            // TODO (Firebase step): replace this with
            // FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pass)...
            Toast.makeText(this, "Firebase login not wired up yet - using Skip for now", Toast.LENGTH_SHORT).show();
        });

        skip.setOnClickListener(v -> goHome());
    }

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}
