package com.sadat.rannaghor.ui.auth;

import android.app.Activity;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.CloudBackupManager;


/** Email/password and Google sign-in, using Firebase's free email-link verification flow. */
public class AuthActivity extends AppCompatActivity {
    public static final String EXTRA_START_CREATE = "start_create_account";

    private static final int MODE_LOGIN = 0;
    private static final int MODE_CREATE = 1;
    private static final int CREATE_EMAIL = 0;
    private static final int CREATE_PASSWORD = 1;
    private static final int CREATE_VERIFY = 2;

    private FirebaseAuth auth;
    private EditText emailInput, passwordInput, confirmPasswordInput, codeInput;
    private TextView title, subtitle, verifiedEmail, status, primary, forgot, resend, modeButton;
    private View passwordLayout, confirmPasswordLayout;
    private int mode = MODE_LOGIN;
    private int createStep = CREATE_EMAIL;
    private String verifiedEmailAddress;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        bindViews();
        findViewById(R.id.buttonAuthBack).setOnClickListener(v -> finish());
        primary.setOnClickListener(v -> onPrimaryAction());
        forgot.setOnClickListener(v -> sendPasswordReset());
        resend.setOnClickListener(v -> sendVerificationEmail());
        modeButton.setOnClickListener(v -> toggleMode());
        findViewById(R.id.buttonAuthGoogle).setOnClickListener(v -> startGoogleSignIn());

        if (!CloudBackupManager.isConfigured(this)) {
            showStatus(getString(R.string.firebase_setup_needed));
        } else {
            auth = FirebaseAuth.getInstance();
        }
        if (getIntent().getBooleanExtra(EXTRA_START_CREATE, false)) mode = MODE_CREATE;
        renderMode();
    }

    private void bindViews() {
        emailInput = findViewById(R.id.editAuthEmail);
        passwordInput = findViewById(R.id.editAuthPassword);
        confirmPasswordInput = findViewById(R.id.editAuthConfirmPassword);
        codeInput = findViewById(R.id.editAuthCode);
        title = findViewById(R.id.textAuthTitle);
        subtitle = findViewById(R.id.textAuthSubtitle);
        verifiedEmail = findViewById(R.id.textVerifiedEmail);
        status = findViewById(R.id.textAuthStatus);
        primary = findViewById(R.id.buttonAuthPrimary);
        forgot = findViewById(R.id.buttonAuthForgot);
        resend = findViewById(R.id.buttonAuthResendCode);
        modeButton = findViewById(R.id.buttonAuthMode);
        passwordLayout = findViewById(R.id.layoutAuthPassword);
        confirmPasswordLayout = findViewById(R.id.layoutAuthConfirmPassword);
    }

    private void toggleMode() {
        if (mode == MODE_LOGIN) {
            mode = MODE_CREATE;
            createStep = CREATE_EMAIL;
        } else {
            mode = MODE_LOGIN;
            createStep = CREATE_EMAIL;
        }
        clearStatus();
        renderMode();
    }

    private void renderMode() {
        boolean loginMode = mode == MODE_LOGIN;
        boolean needEmail = loginMode || createStep == CREATE_EMAIL;
        emailInput.setVisibility(needEmail ? View.VISIBLE : View.GONE);
        emailInput.setEnabled(loginMode || createStep == CREATE_EMAIL);
        verifiedEmail.setVisibility(!loginMode && createStep != CREATE_EMAIL ? View.VISIBLE : View.GONE);
        codeInput.setVisibility(View.GONE);
        passwordLayout.setVisibility(loginMode || (!loginMode && createStep == CREATE_PASSWORD) ? View.VISIBLE : View.GONE);
        confirmPasswordLayout.setVisibility(!loginMode && createStep == CREATE_PASSWORD ? View.VISIBLE : View.GONE);
        forgot.setVisibility(loginMode ? View.VISIBLE : View.GONE);
        resend.setVisibility(!loginMode && createStep == CREATE_VERIFY ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonAuthGoogle).setVisibility(View.VISIBLE);

        if (loginMode) {
            title.setText("Welcome to Rannaghor");
            subtitle.setText("Log in to plan meals and save your favourites.");
            primary.setText("Log in");
            modeButton.setText("Create an account");
        } else if (createStep == CREATE_EMAIL) {
            title.setText("Create your account");
            subtitle.setText("Start with your email address. Firebase will verify it by email link.");
            primary.setText("Continue");
            modeButton.setText("I already have an account · Log in");
        } else if (createStep == CREATE_PASSWORD) {
            title.setText("Set your password");
            subtitle.setText("Create a password of at least six characters. We’ll send a verification link next.");
            verifiedEmail.setText(verifiedEmailAddress);
            primary.setText("Create account");
            modeButton.setText("Back to log in");
        } else {
            title.setText("Verify your email");
            subtitle.setText("Open the verification link in your inbox, then return here and tap I’ve verified.");
            verifiedEmail.setText(verifiedEmailAddress);
            primary.setText("I’ve verified");
            modeButton.setText("Back to log in");
        }
    }

    private void onPrimaryAction() {
        if (auth == null) return;
        if (mode == MODE_LOGIN) login();
        else if (createStep == CREATE_EMAIL) continueCreate();
        else if (createStep == CREATE_PASSWORD) completeSignup();
        else checkEmailVerified();
    }

    private String validatedEmail() {
        String address = emailInput.getText().toString().trim();
        if (!Patterns.EMAIL_ADDRESS.matcher(address).matches()) {
            emailInput.setError("Enter a valid email address");
            return null;
        }
        return address;
    }

    private void login() {
        String address = validatedEmail();
        if (address == null) return;
        String password = passwordInput.getText().toString();
        if (password.isEmpty()) { passwordInput.setError("Enter your password"); return; }
        setBusy(true, "Signing in…");
        auth.signInWithEmailAndPassword(address, password).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) finishSuccessfully();
            else fail(task.getException(), "Could not sign in. Check your email and password.");
        });
    }

    private void continueCreate() {
        String address = validatedEmail();
        if (address == null) return;
        verifiedEmailAddress = address;
        createStep = CREATE_PASSWORD;
        passwordInput.setText("");
        confirmPasswordInput.setText("");
        clearStatus();
        renderMode();
        passwordInput.requestFocus();
    }

    private void completeSignup() {
        String password = passwordInput.getText().toString();
        String confirmation = confirmPasswordInput.getText().toString();
        if (password.length() < 6) { passwordInput.setError("Use at least six characters"); return; }
        if (!password.equals(confirmation)) { confirmPasswordInput.setError("Passwords don’t match"); return; }
        setBusy(true, "Creating your account…");
        auth.createUserWithEmailAndPassword(verifiedEmailAddress, password)
                .addOnSuccessListener(result -> {
                    if (result.getUser() == null) { fail(null, "Account was not created. Please try again."); return; }
                    createStep = CREATE_VERIFY;
                    renderMode();
                    result.getUser().sendEmailVerification()
                            .addOnSuccessListener(unused -> {
                                setBusy(false, "Verification email sent. Check your inbox and spam folder.");
                            })
                            .addOnFailureListener(error -> {
                                renderMode();
                                fail(error, "Account created, but the verification email could not be sent. Tap Resend email.");
                            });
                })
                .addOnFailureListener(error -> fail(error, "Could not create your account."));
    }

    private void checkEmailVerified() {
        if (auth.getCurrentUser() == null) {
            fail(null, "Sign in again, then open the verification email and return here.");
            return;
        }
        setBusy(true, "Checking email verification…");
        auth.getCurrentUser().reload().addOnCompleteListener(this, task -> {
            if (task.isSuccessful() && auth.getCurrentUser() != null && auth.getCurrentUser().isEmailVerified()) {
                finishSuccessfully();
            } else if (!task.isSuccessful()) {
                fail(task.getException(), "Could not check verification yet. Try again.");
            } else {
                setBusy(false, "We haven’t received confirmation yet. Open the email link, then try again.");
            }
        });
    }

    private void sendVerificationEmail() {
        if (auth.getCurrentUser() == null) { showStatus("Create your account first, then request the verification email."); return; }
        setBusy(true, "Sending verification email…");
        auth.getCurrentUser().sendEmailVerification()
                .addOnSuccessListener(unused -> setBusy(false, "A new verification link was sent. Check your inbox and spam folder."))
                .addOnFailureListener(error -> fail(error, "Could not resend the verification email."));
    }

    private void sendPasswordReset() {
        String address = validatedEmail();
        if (address == null) return;
        setBusy(true, "Sending password reset email…");
        auth.sendPasswordResetEmail(address)
                .addOnSuccessListener(unused -> setBusy(false, "If an account exists for that email, a reset link has been sent."))
                .addOnFailureListener(error -> fail(error, "Could not send the password reset email."));
    }

    private void startGoogleSignIn() {
        if (auth == null) return;
        setBusy(true, "Opening Google sign-in…");
        GoogleSignInHelper.requestCredential(this, new GoogleSignInHelper.Callback() {
            @Override public void onCredential(AuthCredential credential) {
                auth.signInWithCredential(credential).addOnCompleteListener(AuthActivity.this, task -> {
                    if (task.isSuccessful()) finishSuccessfully();
                    else fail(task.getException(), "Could not sign in with Google.");
                });
            }

            @Override public void onError(Exception error) {
                fail(error, "Google sign-in was cancelled or could not start.");
            }
        });
    }

    private void finishSuccessfully() {
        setBusy(false, null);
        setResult(Activity.RESULT_OK);
        finish();
    }

    private void fail(Exception error, String fallback) {
        setBusy(false, error == null || error.getLocalizedMessage() == null
                ? fallback : fallback + "\n" + error.getLocalizedMessage());
    }

    private void showStatus(String message) {
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    private void clearStatus() {
        status.setText("");
        status.setVisibility(View.GONE);
    }

    private void setBusy(boolean busy, @Nullable String message) {
        primary.setEnabled(!busy);
        findViewById(R.id.buttonAuthGoogle).setEnabled(!busy);
        forgot.setEnabled(!busy);
        resend.setEnabled(!busy);
        if (busy) primary.setAlpha(0.55f); else primary.setAlpha(1f);
        if (message == null) clearStatus(); else showStatus(message);
    }
}
