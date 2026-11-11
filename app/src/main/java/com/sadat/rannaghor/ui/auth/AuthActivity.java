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
import com.google.firebase.functions.FirebaseFunctions;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.CloudBackupManager;

import java.util.HashMap;
import java.util.Map;

/** Email/password and Google sign-in, with a server-verified email-code signup flow. */
public class AuthActivity extends AppCompatActivity {
    public static final String EXTRA_START_CREATE = "start_create_account";

    private static final int MODE_LOGIN = 0;
    private static final int MODE_CREATE = 1;
    private static final int CREATE_EMAIL = 0;
    private static final int CREATE_CODE = 1;
    private static final int CREATE_PASSWORD = 2;

    private FirebaseAuth auth;
    private FirebaseFunctions functions;
    private EditText emailInput, passwordInput, confirmPasswordInput, codeInput;
    private TextView title, subtitle, verifiedEmail, status, primary, forgot, resend, modeButton;
    private View passwordLayout, confirmPasswordLayout;
    private int mode = MODE_LOGIN;
    private int createStep = CREATE_EMAIL;
    private String verifiedEmailAddress;
    private String verificationTicket;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        bindViews();
        findViewById(R.id.buttonAuthBack).setOnClickListener(v -> finish());
        primary.setOnClickListener(v -> onPrimaryAction());
        forgot.setOnClickListener(v -> sendPasswordReset());
        resend.setOnClickListener(v -> sendVerificationCode());
        modeButton.setOnClickListener(v -> toggleMode());
        findViewById(R.id.buttonAuthGoogle).setOnClickListener(v -> startGoogleSignIn());

        if (!CloudBackupManager.isConfigured(this)) {
            showStatus(getString(R.string.firebase_setup_needed));
        } else {
            auth = FirebaseAuth.getInstance();
            functions = FirebaseFunctions.getInstance("us-central1");
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
            verificationTicket = null;
        }
        clearStatus();
        renderMode();
    }

    private void renderMode() {
        boolean loginMode = mode == MODE_LOGIN;
        boolean needEmail = loginMode || createStep != CREATE_PASSWORD;
        emailInput.setVisibility(needEmail ? View.VISIBLE : View.GONE);
        emailInput.setEnabled(loginMode || createStep == CREATE_EMAIL);
        verifiedEmail.setVisibility(!loginMode && createStep == CREATE_PASSWORD ? View.VISIBLE : View.GONE);
        codeInput.setVisibility(!loginMode && createStep == CREATE_CODE ? View.VISIBLE : View.GONE);
        passwordLayout.setVisibility(loginMode || (!loginMode && createStep == CREATE_PASSWORD) ? View.VISIBLE : View.GONE);
        confirmPasswordLayout.setVisibility(!loginMode && createStep == CREATE_PASSWORD ? View.VISIBLE : View.GONE);
        forgot.setVisibility(loginMode ? View.VISIBLE : View.GONE);
        resend.setVisibility(!loginMode && createStep == CREATE_CODE ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonAuthGoogle).setVisibility(View.VISIBLE);

        if (loginMode) {
            title.setText("Welcome to Rannaghor");
            subtitle.setText("Log in to plan meals and save your favourites.");
            primary.setText("Log in");
            modeButton.setText("Create an account");
        } else if (createStep == CREATE_EMAIL) {
            title.setText("Create your account");
            subtitle.setText("We’ll email you a six-digit verification code first.");
            primary.setText("Send verification code");
            modeButton.setText("I already have an account · Log in");
        } else if (createStep == CREATE_CODE) {
            title.setText("Verify your email");
            subtitle.setText("Enter the six-digit code we sent to your email address.");
            primary.setText("Verify code");
            modeButton.setText("Back to log in");
        } else {
            title.setText("Set your password");
            subtitle.setText("Your email is verified. Create a password of at least six characters.");
            verifiedEmail.setText(verifiedEmailAddress);
            primary.setText("Create account");
            modeButton.setText("Back to log in");
        }
    }

    private void onPrimaryAction() {
        if (auth == null) return;
        if (mode == MODE_LOGIN) login();
        else if (createStep == CREATE_EMAIL) sendVerificationCode();
        else if (createStep == CREATE_CODE) verifyCode();
        else completeSignup();
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

    private void sendVerificationCode() {
        String address = validatedEmail();
        if (address == null) return;
        if (functions == null) { showStatus("Email verification isn’t configured for this Firebase project yet."); return; }
        setBusy(true, "Sending verification code…");
        Map<String, Object> data = new HashMap<>();
        data.put("email", address);
        functions.getHttpsCallable("requestEmailCode").call(data)
                .addOnSuccessListener(result -> {
                    verifiedEmailAddress = address;
                    createStep = CREATE_CODE;
                    codeInput.setText("");
                    setBusy(false, "Check your inbox for the six-digit code.");
                    renderMode();
                    codeInput.requestFocus();
                })
                .addOnFailureListener(error -> fail(error,
                        "Could not send the code. The secure email-code service may not be deployed yet."));
    }

    private void verifyCode() {
        String code = codeInput.getText().toString().trim();
        if (code.length() != 6 || !code.matches("[0-9]{6}")) {
            codeInput.setError("Enter the six-digit code");
            return;
        }
        setBusy(true, "Verifying code…");
        Map<String, Object> data = new HashMap<>();
        data.put("email", verifiedEmailAddress);
        data.put("code", code);
        functions.getHttpsCallable("verifyEmailCode").call(data)
                .addOnSuccessListener(result -> {
                    Object payload = result.getData();
                    if (payload instanceof Map) {
                        Object ticket = ((Map<?, ?>) payload).get("verificationTicket");
                        verificationTicket = ticket instanceof String ? (String) ticket : null;
                    }
                    if (verificationTicket == null || verificationTicket.isEmpty()) {
                        fail(null, "The server didn’t return a verification session. Try again.");
                        return;
                    }
                    createStep = CREATE_PASSWORD;
                    passwordInput.setText("");
                    confirmPasswordInput.setText("");
                    setBusy(false, "Email verified.");
                    renderMode();
                    passwordInput.requestFocus();
                })
                .addOnFailureListener(error -> fail(error, "That code is invalid or expired."));
    }

    private void completeSignup() {
        String password = passwordInput.getText().toString();
        String confirmation = confirmPasswordInput.getText().toString();
        if (password.length() < 6) { passwordInput.setError("Use at least six characters"); return; }
        if (!password.equals(confirmation)) { confirmPasswordInput.setError("Passwords don’t match"); return; }
        if (verificationTicket == null) { fail(null, "Verify your email again before setting a password."); return; }
        setBusy(true, "Creating your account…");
        Map<String, Object> data = new HashMap<>();
        data.put("email", verifiedEmailAddress);
        data.put("verificationTicket", verificationTicket);
        data.put("password", password);
        functions.getHttpsCallable("completeEmailSignup").call(data)
                .addOnSuccessListener(result -> {
                    Object payload = result.getData();
                    Object token = payload instanceof Map ? ((Map<?, ?>) payload).get("customToken") : null;
                    if (!(token instanceof String)) { fail(null, "The server didn’t finish creating the account."); return; }
                    auth.signInWithCustomToken((String) token).addOnCompleteListener(this, signIn -> {
                        if (signIn.isSuccessful()) finishSuccessfully();
                        else fail(signIn.getException(), "Account created, but automatic sign-in failed. Please log in.");
                    });
                })
                .addOnFailureListener(error -> fail(error, "Could not create your account."));
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
