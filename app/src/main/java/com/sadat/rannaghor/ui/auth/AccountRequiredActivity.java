package com.sadat.rannaghor.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.CloudBackupManager;

/** Base gate for screens whose contents belong to a signed-in account. */
public abstract class AccountRequiredActivity extends AppCompatActivity {
    private static final int REQUEST_ACCOUNT = 420;

    protected final boolean requireSignedInAccount() {
        if (CloudBackupManager.isConfigured(this)
                && FirebaseAuth.getInstance().getCurrentUser() != null) return true;
        if (!CloudBackupManager.isConfigured(this)) {
            Toast.makeText(this, R.string.firebase_setup_needed, Toast.LENGTH_LONG).show();
            finish();
        } else {
            startActivityForResult(new Intent(this, AuthActivity.class), REQUEST_ACCOUNT);
        }
        return false;
    }

    /** Restore this account's cloud snapshot before a personal list is displayed. */
    protected final void restoreAccountData(Runnable onComplete) {
        CloudBackupManager.restoreAndBackup(this, error -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                Toast.makeText(this, "Could not sync cloud backup. Showing this account's saved data.", Toast.LENGTH_LONG).show();
            }
            onComplete.run();
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ACCOUNT) return;
        if (resultCode == RESULT_OK && CloudBackupManager.isConfigured(this)
                && FirebaseAuth.getInstance().getCurrentUser() != null) {
            recreate();
        } else {
            finish();
        }
    }
}
