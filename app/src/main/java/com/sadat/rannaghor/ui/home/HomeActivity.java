package com.sadat.rannaghor.ui.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;

import android.Manifest;
import android.content.pm.PackageManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.sadat.rannaghor.R;
import com.sadat.rannaghor.data.repository.CloudBackupManager;
import com.sadat.rannaghor.data.repository.RecipeReviewRepository;
import com.sadat.rannaghor.data.repository.UserProfileRepository;
import com.sadat.rannaghor.data.local.AppDatabase;
import com.sadat.rannaghor.model.RecipeCategory;
import com.sadat.rannaghor.ui.category.RecipeCategoryActivity;
import com.sadat.rannaghor.ui.favorites.FavoritesActivity;
import com.sadat.rannaghor.ui.planner.PlannerActivity;
import com.sadat.rannaghor.ui.auth.AuthActivity;
import com.sadat.rannaghor.ui.auth.GoogleSignInHelper;
import com.sadat.rannaghor.ui.recipes.RecipesActivity;
import com.sadat.rannaghor.ui.shopping.ShoppingListActivity;
import com.sadat.rannaghor.util.ReminderScheduler;
import com.sadat.rannaghor.util.PlanReminderScheduler;

import java.util.List;

/** Main navigation hub and account entry point. */
public class HomeActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    private boolean firebaseConfigured;
    private View accountPanel;
    private TextView loginButton, createAccountButton;
    private ImageView homeAvatar;
    private ActivityResultLauncher<String> profilePicker;
    private static final int REQUEST_ACCOUNT_AUTH = 411;
    private static final int REQUEST_PRIVATE_FEATURE_AUTH = 412;
    private Class<?> pendingDestination;
    private String switchingFromUid;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profilePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onProfileImageSelected);
        setContentView(R.layout.activity_home);

        firebaseConfigured = CloudBackupManager.isConfigured(this);
        if (firebaseConfigured) {
            auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) syncSignedInUser();
        }
        ReminderScheduler.scheduleDailyReminder(this);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }

        accountPanel = findViewById(R.id.accountPanel);
        homeAvatar = findViewById(R.id.imageHomeAvatar);
        loginButton = findViewById(R.id.buttonLogin);
        createAccountButton = findViewById(R.id.buttonCreateAccount);

        homeAvatar.setOnClickListener(v -> toggleAccountPanel());
        loginButton.setOnClickListener(v -> launchAuth(false));
        createAccountButton.setOnClickListener(v -> launchAuth(true));

        findViewById(R.id.actionRecipes).setOnClickListener(v -> startActivity(new Intent(this, RecipesActivity.class)));
        findViewById(R.id.actionPlanner).setOnClickListener(v -> openPersonalDestination(PlannerActivity.class));
        findViewById(R.id.actionShopping).setOnClickListener(v -> openPersonalDestination(ShoppingListActivity.class));
        findViewById(R.id.actionFavorites).setOnClickListener(v -> openPersonalDestination(FavoritesActivity.class));

        findViewById(R.id.categoryQuickEasy).setOnClickListener(v -> openCategory(RecipeCategory.QUICK_EASY));
        findViewById(R.id.categoryDinner).setOnClickListener(v -> openCategory(RecipeCategory.DINNER));
        findViewById(R.id.categoryVegetarian).setOnClickListener(v -> openCategory(RecipeCategory.VEGETARIAN));
        findViewById(R.id.categoryHealthy).setOnClickListener(v -> openCategory(RecipeCategory.HEALTHY));
        findViewById(R.id.categoryInstantPot).setOnClickListener(v -> openCategory(RecipeCategory.INSTANT_POT));
        findViewById(R.id.categoryVegan).setOnClickListener(v -> openCategory(RecipeCategory.VEGAN));
        findViewById(R.id.categorySoups).setOnClickListener(v -> openCategory(RecipeCategory.SOUPS));
        findViewById(R.id.categorySalads).setOnClickListener(v -> openCategory(RecipeCategory.SALADS));
        findViewById(R.id.categoryBangladeshi).setOnClickListener(v -> openCategory(RecipeCategory.BANGLADESHI));
        updateAccountSummary();
    }

    private void openCategory(String categoryId) {
        Intent intent = new Intent(this, RecipeCategoryActivity.class);
        intent.putExtra(RecipeCategoryActivity.EXTRA_CATEGORY, categoryId);
        startActivity(intent);
    }

    private void openPersonalDestination(Class<?> destination) {
        if (auth != null && auth.getCurrentUser() != null) {
            startActivity(new Intent(this, destination));
            return;
        }
        if (!firebaseConfigured) {
            Toast.makeText(this, R.string.firebase_setup_needed, Toast.LENGTH_LONG).show();
            return;
        }
        pendingDestination = destination;
        startActivityForResult(new Intent(this, AuthActivity.class), REQUEST_PRIVATE_FEATURE_AUTH);
    }

    private void toggleAccountPanel() {
        if (auth != null && auth.getCurrentUser() != null) {
            showAccountDialog();
            return;
        }
        accountPanel.setVisibility(accountPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void showAccountDialog() {
        if (auth == null || auth.getCurrentUser() == null) return;
        String userId = auth.getCurrentUser().getUid();
        View content = getLayoutInflater().inflate(R.layout.dialog_account, null, false);
        TextView email = content.findViewById(R.id.textAccountEmail);
        TextView ratingCount = content.findViewById(R.id.textAccountRatingCount);
        TextView reviewCount = content.findViewById(R.id.textAccountReviewCount);
        TextView countError = content.findViewById(R.id.textAccountCountError);
        ImageView avatar = content.findViewById(R.id.imageAccountPhoto);
        loadProfilePhoto(userId, avatar);
        email.setText(auth.getCurrentUser().getEmail() == null ? "Signed-in account" : auth.getCurrentUser().getEmail());
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .create();
        content.findViewById(R.id.buttonSwitchAccount).setOnClickListener(v -> {
            dialog.dismiss();
            switchingFromUid = auth.getCurrentUser().getUid();
            startActivityForResult(new Intent(this, AuthActivity.class), REQUEST_ACCOUNT_AUTH);
        });
        content.findViewById(R.id.buttonAccountLogout).setOnClickListener(v -> {
            dialog.dismiss();
            PlanReminderScheduler.cancelAllForUser(getApplicationContext(), userId);
            auth.signOut();
            updateAccountSummary();
            Toast.makeText(this, "You are signed out", Toast.LENGTH_SHORT).show();
        });
        content.findViewById(R.id.buttonChooseProfilePhoto).setOnClickListener(v -> {
            dialog.dismiss();
            profilePicker.launch("image/*");
        });
        content.findViewById(R.id.buttonChangePassword).setOnClickListener(v -> {
            dialog.dismiss();
            openChangePasswordDialog();
        });
        content.findViewById(R.id.buttonDeleteAccount).setOnClickListener(v -> {
            dialog.dismiss();
            confirmAccountDeletion();
        });
        new RecipeReviewRepository(this).getUserReviewCounts(userId, new RecipeReviewRepository.UserReviewCountsCallback() {
            @Override public void onLoaded(int ratings, int writtenReviews) {
                if (dialog.isShowing()) {
                    ratingCount.setText(String.valueOf(ratings));
                    reviewCount.setText(String.valueOf(writtenReviews));
                }
            }
            @Override public void onError(Exception error) {
                if (dialog.isShowing()) {
                    ratingCount.setText("—");
                    reviewCount.setText("—");
                    countError.setVisibility(View.VISIBLE);
                }
            }
        });
        dialog.show();
    }

    private void launchAuth(boolean createAccount) {
        if (!firebaseConfigured || auth == null) {
            Toast.makeText(this, R.string.firebase_setup_needed, Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, AuthActivity.class);
        intent.putExtra(AuthActivity.EXTRA_START_CREATE, createAccount);
        startActivityForResult(intent, REQUEST_ACCOUNT_AUTH);
    }

    private void syncSignedInUser() {
        CloudBackupManager.restoreAndBackup(this, error -> {
            if (isFinishing()) return;
            updateAccountSummary();
            if (error != null) {
                String reason = error.getLocalizedMessage() == null ? "Check your internet connection and Firestore rules." : error.getLocalizedMessage();
                Toast.makeText(this, getString(R.string.cloud_sync_failed, reason), Toast.LENGTH_LONG).show();
            } else Toast.makeText(this, R.string.cloud_sync_succeeded, Toast.LENGTH_SHORT).show();
            PlanReminderScheduler.scheduleAll(getApplicationContext());
        });
    }

    private void updateAccountSummary() {
        boolean signedIn = auth != null && auth.getCurrentUser() != null;
        homeAvatar.setContentDescription(signedIn ? "Account menu" : "Sign in or create an account");
        if (signedIn) {
            accountPanel.setVisibility(View.GONE);
            loadProfilePhoto(auth.getCurrentUser().getUid(), homeAvatar);
        } else if (homeAvatar != null) {
            Glide.with(this).clear(homeAvatar);
            homeAvatar.setImageResource(R.drawable.ic_profile);
        }
    }

    private void loadProfilePhoto(String uid, ImageView target) {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        String googlePhoto = user == null || user.getPhotoUrl() == null ? null : user.getPhotoUrl().toString();
        Glide.with(target).load(googlePhoto).circleCrop().placeholder(R.drawable.ic_profile).into(target);
        UserProfileRepository.loadAvatar(uid, encoded -> {
            if (isFinishing() || encoded == null || encoded.isEmpty()) return;
            byte[] image = UserProfileRepository.decodeAvatar(encoded);
            if (image != null) Glide.with(target).load(image).circleCrop().placeholder(R.drawable.ic_profile).into(target);
        });
    }

    private void onProfileImageSelected(Uri uri) {
        if (uri == null || auth == null || auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();
        Toast.makeText(this, "Saving profile photo…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String encoded = UserProfileRepository.compressAvatar(this, uri);
                UserProfileRepository.saveAvatar(uid, encoded, error -> {
                    if (isFinishing()) return;
                    if (error != null) Toast.makeText(this, "Could not save profile photo: " + error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    else {
                        updateAccountSummary();
                        Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "Could not read that image.", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void openChangePasswordDialog() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null) return;
        if (!hasProvider(user, "password")) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Google account password")
                    .setMessage("This account signs in with Google. Manage its password in your Google Account.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open Google Account", (dialog, which) -> {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://myaccount.google.com/security"))); }
                        catch (Exception ignored) { Toast.makeText(this, "Could not open your browser.", Toast.LENGTH_SHORT).show(); }
                    }).show();
            return;
        }
        View content = getLayoutInflater().inflate(R.layout.dialog_change_password, null, false);
        EditText current = content.findViewById(R.id.editCurrentPassword);
        EditText next = content.findViewById(R.id.editNewPassword);
        EditText confirm = content.findViewById(R.id.editConfirmPassword);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Change password")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Update password", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String oldPassword = current.getText().toString();
            String newPassword = next.getText().toString();
            if (oldPassword.isEmpty()) { current.setError("Enter your current password"); return; }
            if (newPassword.length() < 6) { next.setError("Use at least six characters"); return; }
            if (!newPassword.equals(confirm.getText().toString())) { confirm.setError("Passwords don’t match"); return; }
            String email = user.getEmail();
            if (email == null) { Toast.makeText(this, "This account has no email address.", Toast.LENGTH_LONG).show(); return; }
            user.reauthenticate(EmailAuthProvider.getCredential(email, oldPassword)).addOnSuccessListener(unused ->
                    user.updatePassword(newPassword).addOnSuccessListener(done -> {
                        dialog.dismiss();
                        Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(error -> Toast.makeText(this, "Could not update password: " + error.getLocalizedMessage(), Toast.LENGTH_LONG).show())
            ).addOnFailureListener(error -> Toast.makeText(this, "Current password was not accepted.", Toast.LENGTH_LONG).show());
        }));
        dialog.show();
    }

    private void confirmAccountDeletion() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete account permanently?")
                .setMessage("This deletes your sign-in, cloud backup, profile photo, ratings and reviews, and this phone’s favourites, meal plans, and shopping list. This can’t be undone.")
                .setNegativeButton("Keep account", null)
                .setPositiveButton("Continue", (dialog, which) -> reauthenticateForDeletion(user))
                .show();
    }

    private void reauthenticateForDeletion(FirebaseUser user) {
        if (hasProvider(user, "password")) {
            View content = getLayoutInflater().inflate(R.layout.dialog_confirm_password, null, false);
            EditText password = content.findViewById(R.id.editDeleteConfirmPassword);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Confirm it’s you")
                    .setMessage("Enter your password to delete this account.")
                    .setView(content)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Verify and delete", (dialog, which) -> {
                        String email = user.getEmail();
                        if (email == null) return;
                        AuthCredential credential = EmailAuthProvider.getCredential(email, password.getText().toString());
                        user.reauthenticate(credential).addOnSuccessListener(unused -> deleteAccountData(user))
                                .addOnFailureListener(error -> Toast.makeText(this, "Password was not accepted.", Toast.LENGTH_LONG).show());
                    }).show();
        } else if (hasProvider(user, "google.com")) {
            GoogleSignInHelper.requestCredential(this, new GoogleSignInHelper.Callback() {
                @Override public void onCredential(AuthCredential credential) {
                    user.reauthenticate(credential).addOnSuccessListener(unused -> deleteAccountData(user))
                            .addOnFailureListener(error -> Toast.makeText(HomeActivity.this, "Could not verify this Google account.", Toast.LENGTH_LONG).show());
                }
                @Override public void onError(Exception error) {
                    Toast.makeText(HomeActivity.this, error.getLocalizedMessage() == null ? "Google verification was cancelled." : error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            Toast.makeText(this, "Log in again with this account, then retry deletion.", Toast.LENGTH_LONG).show();
        }
    }

    private void deleteAccountData(FirebaseUser user) {
        String uid = user.getUid();
        new Thread(() -> {
            List<com.sadat.rannaghor.model.MealPlanEntry> plans = AppDatabase.getInstanceForUser(getApplicationContext(), uid).mealPlanDao().getAll();
            for (com.sadat.rannaghor.model.MealPlanEntry plan : plans) {
                PlanReminderScheduler.cancel(getApplicationContext(), plan.id, uid);
            }
            runOnUiThread(() -> deleteCloudDataThenAccount(user));
        }).start();
    }

    private void deleteCloudDataThenAccount(FirebaseUser user) {
        String uid = user.getUid();
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        Task<Void> backup = firestore.collection("users").document(uid).collection("backups").document("current").delete();
        Task<Void> profile = firestore.collection("users").document(uid).collection("profile").document("details").delete();
        Tasks.whenAll(backup, profile)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return deleteReviewBatch(uid);
                })
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return user.delete();
                })
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(this, "Could not delete account data: " + (task.getException() == null ? "check your connection and Firestore rules." : task.getException().getLocalizedMessage()), Toast.LENGTH_LONG).show();
                        return;
                    }
                    AppDatabase.deleteUserDatabase(getApplicationContext(), uid);
                    auth.signOut();
                    updateAccountSummary();
                    Toast.makeText(this, "Account and its data deleted", Toast.LENGTH_LONG).show();
                });
    }

    private Task<Void> deleteReviewBatch(String uid) {
        TaskCompletionSource<Void> completion = new TaskCompletionSource<>();
        deleteNextReviewBatch(uid, completion);
        return completion.getTask();
    }

    private void deleteNextReviewBatch(String uid, TaskCompletionSource<Void> completion) {
        FirebaseFirestore.getInstance().collection("recipeReviews")
                .whereEqualTo("uid", uid).limit(400).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) { completion.setResult(null); return; }
                    com.google.firebase.firestore.WriteBatch batch = FirebaseFirestore.getInstance().batch();
                    for (DocumentSnapshot review : snapshot.getDocuments()) batch.delete(review.getReference());
                    batch.commit().addOnSuccessListener(unused -> {
                        if (snapshot.size() < 400) completion.setResult(null);
                        else deleteNextReviewBatch(uid, completion);
                    }).addOnFailureListener(completion::setException);
                }).addOnFailureListener(completion::setException);
    }

    private boolean hasProvider(FirebaseUser user, String provider) {
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if (provider.equals(info.getProviderId())) return true;
        }
        return false;
    }

    @Override protected void onResume() {
        super.onResume();
        if (homeAvatar != null) updateAccountSummary();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ACCOUNT_AUTH) {
            updateAccountSummary();
            if (resultCode == RESULT_OK && auth != null && auth.getCurrentUser() != null) {
                String nowUid = auth.getCurrentUser().getUid();
                if (switchingFromUid != null && !switchingFromUid.equals(nowUid)) {
                    PlanReminderScheduler.cancelAllForUser(getApplicationContext(), switchingFromUid);
                }
                switchingFromUid = null;
                syncSignedInUser();
            }
        } else if (requestCode == REQUEST_PRIVATE_FEATURE_AUTH) {
            Class<?> destination = pendingDestination;
            pendingDestination = null;
            if (resultCode == RESULT_OK && auth != null && auth.getCurrentUser() != null && destination != null) {
                CloudBackupManager.restoreAndBackup(this, error -> {
                    if (isFinishing()) return;
                    updateAccountSummary();
                    if (error != null) Toast.makeText(this, "Could not sync cloud backup. Showing this account's saved data.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, destination));
                });
            } else {
                updateAccountSummary();
            }
        }
    }

}
