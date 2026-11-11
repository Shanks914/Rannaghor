package com.sadat.rannaghor;

import android.app.Application;
import android.content.pm.ApplicationInfo;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/** Installs the App Check provider used by protected Firebase callable functions. */
public class RannaghorApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        if (FirebaseApp.getApps(this).isEmpty()) return;
        FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance();
        boolean debugBuild = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (debugBuild) {
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());
        } else {
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
        }
    }
}
