package com.autoanswerwa.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

/**
 * Activity transparent care se deschide PESTE lock screen
 * si redirectioneaza catre WhatsApp.
 * Rezolva problema "video paused" cand ecranul e blocat.
 */
public class UnlockActivity extends Activity {

    private static final String TAG = "UnlockActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "UnlockActivity pornit - deblochez ecranul");

        // Afiseaza aceasta Activity PESTE lock screen
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        // Pe Android 8+ foloseste API nou
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = getSystemService(KeyguardManager.class);
            if (km != null) {
                km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                    @Override
                    public void onDismissSucceeded() {
                        Log.i(TAG, "Lock screen deblocat!");
                        launchWhatsApp();
                    }
                    @Override
                    public void onDismissError() {
                        Log.w(TAG, "Nu s-a putut debloca - lansez WhatsApp oricum");
                        launchWhatsApp();
                    }
                    @Override
                    public void onDismissCancelled() {
                        launchWhatsApp();
                    }
                });
            } else {
                launchWhatsApp();
            }
        } else {
            launchWhatsApp();
        }
    }

    private void launchWhatsApp() {
        try {
            String pkg = getIntent().getStringExtra("package");
            if (pkg == null) pkg = "com.whatsapp";
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Eroare launch WhatsApp: " + e.getMessage());
        }
        finish();
    }
}
