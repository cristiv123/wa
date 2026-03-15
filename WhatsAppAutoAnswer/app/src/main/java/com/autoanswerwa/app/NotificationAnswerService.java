package com.autoanswerwa.app;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.WindowManager;

public class NotificationAnswerService extends NotificationListenerService {

    private static final String TAG = "WA_NotifAnswer";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b";

    private Handler handler;
    private long lastAnsweredTime = -99999;
    private static final long COOLDOWN_MS = 15000;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        Log.i(TAG, "NotificationAnswerService pornit");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String pkg = sbn.getPackageName();
        if (!pkg.equals(WHATSAPP_PACKAGE) && !pkg.equals(WHATSAPP_BUSINESS_PACKAGE)) return;

        long now = System.currentTimeMillis();
        if (now - lastAnsweredTime < COOLDOWN_MS) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        String title = "";
        String text = "";
        if (notification.extras != null) {
            CharSequence t = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence b = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (t != null) title = t.toString().toLowerCase();
            if (b != null) text = b.toString().toLowerCase();
        }

        Log.d(TAG, "Notificare: title='" + title + "' text='" + text + "'");

        // Detecteaza apel incoming
        boolean isCall =
            text.contains("incoming video call") ||
            text.contains("incoming voice call") ||
            text.contains("video call") ||
            text.contains("voice call") ||
            text.contains("apel video") ||
            text.contains("apel vocal") ||
            text.contains("calling") ||
            title.contains("incoming");

        if (!isCall) return;

        Log.i(TAG, "Apel detectat! Trezesc ecranul...");
        lastAnsweredTime = now;

        // Pasul 1: Trezeste ecranul IMEDIAT
        wakeScreenFully();

        // Pasul 2: Incearca sa raspunda prin notificare
        tryAnswerViaNotification(notification);

        // Pasul 3: Deschide WhatsApp direct (fallback)
        // Accessibility Service va apasa Answer cand ecranul e pornit
        handler.postDelayed(() -> {
            try {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launchIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Eroare launch WhatsApp: " + e.getMessage());
            }
        }, 500);
    }

    private void tryAnswerViaNotification(Notification notification) {
        Notification.Action[] actions = notification.actions;
        if (actions == null) return;

        // Prioritate 1: cauta actiunea VIDEO Answer
        for (Notification.Action action : actions) {
            if (action == null || action.title == null) continue;
            String actionTitle = action.title.toString().toLowerCase();
            Log.d(TAG, "Actiune: " + actionTitle);
            if ((actionTitle.contains("video") || actionTitle.contains("answer video")) &&
                !actionTitle.contains("decline") && !actionTitle.contains("refuz")) {
                try {
                    action.actionIntent.send();
                    Log.i(TAG, "Raspuns VIDEO prin notificare: " + actionTitle);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Eroare intent video: " + e.getMessage());
                }
            }
        }

        // Prioritate 2: orice Answer
        for (Notification.Action action : actions) {
            if (action == null || action.title == null) continue;
            String actionTitle = action.title.toString().toLowerCase();
            if ((actionTitle.contains("answer") || actionTitle.contains("accept") ||
                 actionTitle.contains("raspunde")) &&
                !actionTitle.contains("decline") && !actionTitle.contains("refuz")) {
                try {
                    action.actionIntent.send();
                    Log.i(TAG, "Raspuns AUDIO prin notificare: " + actionTitle);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Eroare intent audio: " + e.getMessage());
                }
            }
        }
    }

    private void wakeScreenFully() {
        try {
            // 1. PowerManager WakeLock - trezeste CPU + ecran
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                PowerManager.ACQUIRE_CAUSES_WAKEUP |
                PowerManager.ON_AFTER_RELEASE,
                "WA:FullWake"
            );
            wl.acquire(20000);
            handler.postDelayed(() -> { if (wl.isHeld()) wl.release(); }, 18000);

            // 2. Keyguard - dezactiveaza lock screen temporar
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isKeyguardLocked()) {
                Log.d(TAG, "Keyguard activ - incerc sa il dezactivez");
            }

            Log.i(TAG, "Ecran trezit cu succes");
        } catch (Exception e) {
            Log.e(TAG, "WakeLock error: " + e.getMessage());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}
}
