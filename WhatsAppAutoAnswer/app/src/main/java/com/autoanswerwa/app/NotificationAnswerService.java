package com.autoanswerwa.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * NotificationAnswerService - raspunde la apeluri WhatsApp
 * prin actiunile din notificare (ca Skype).
 * Functioneaza si cand ecranul e NEGRU/STINS!
 */
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

        // Verifica daca e notificare de apel incoming
        String title = "";
        String text = "";
        if (notification.extras != null) {
            CharSequence t = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence b = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (t != null) title = t.toString().toLowerCase();
            if (b != null) text = b.toString().toLowerCase();
        }

        Log.d(TAG, "Notificare WhatsApp: title=" + title + " text=" + text);

        boolean isIncomingCall =
            text.contains("incoming video call") ||
            text.contains("incoming voice call") ||
            text.contains("video call") ||
            text.contains("voice call") ||
            text.contains("apel video") ||
            text.contains("apel vocal") ||
            title.contains("incoming") ||
            text.contains("calling");

        if (!isIncomingCall) return;

        Log.i(TAG, "Apel detectat in notificare! Caut butonul Answer...");

        // Cauta actiunea "Answer" in notificare
        Notification.Action[] actions = notification.actions;
        if (actions == null) {
            Log.w(TAG, "Nu exista actiuni in notificare");
            return;
        }

        for (Notification.Action action : actions) {
            if (action == null || action.title == null) continue;
            String actionTitle = action.title.toString().toLowerCase();
            Log.d(TAG, "Actiune gasita: " + actionTitle);

            if (actionTitle.contains("answer") ||
                actionTitle.contains("accept") ||
                actionTitle.contains("video") ||
                actionTitle.contains("raspunde") ||
                actionTitle.contains("accepta")) {

                // Exclude decline/refuz
                if (actionTitle.contains("decline") || actionTitle.contains("refuz")) continue;

                try {
                    // Trezeste ecranul
                    wakeScreen();

                    // Trimite actiunea cu delay mic
                    final PendingIntent intent = action.actionIntent;
                    handler.postDelayed(() -> {
                        try {
                            intent.send();
                            lastAnsweredTime = System.currentTimeMillis();
                            Log.i(TAG, "Apel raspuns prin notificare: " + actionTitle);
                        } catch (Exception e) {
                            Log.e(TAG, "Eroare la trimitere intent: " + e.getMessage());
                        }
                    }, 1000);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Eroare: " + e.getMessage());
                }
            }
        }
        Log.w(TAG, "Nu am gasit buton Answer in notificare");
    }

    private void wakeScreen() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "WA:AnswerWake"
            );
            wl.acquire(10000);
            handler.postDelayed(() -> { if (wl.isHeld()) wl.release(); }, 8000);
        } catch (Exception e) {
            Log.e(TAG, "WakeLock error: " + e.getMessage());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}
}
