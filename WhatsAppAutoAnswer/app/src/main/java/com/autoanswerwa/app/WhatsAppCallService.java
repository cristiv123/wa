package com.autoanswerwa.app;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class WhatsAppCallService extends AccessibilityService {

    private static final String TAG = "WA_AutoAnswer";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b";

    private static final String[] ALL_ANSWER_IDS = {
        "com.whatsapp:id/answer_video_call_btn",
        "com.whatsapp:id/video_call_btn",
        "com.whatsapp:id/call_btn_answer_video",
        "com.whatsapp:id/answer_video",
        "com.whatsapp:id/answer_call_btn",
        "com.whatsapp:id/answer_voice_btn",
        "com.whatsapp:id/call_btn_answer_voice",
        "com.whatsapp:id/answer",
        "com.whatsapp:id/btn_answer",
        "com.whatsapp:id/call_answer_btn",
        "com.whatsapp.w4b:id/answer_video_call_btn",
        "com.whatsapp.w4b:id/answer_call_btn",
        "com.whatsapp.w4b:id/answer_video",
        "com.whatsapp.w4b:id/answer",
    };

    private static final String[] ANSWER_BUTTON_TEXTS = {
        "answer", "raspunde", "accepta", "accept",
        "antworten", "répondre", "risposta"
    };

    private static final String[] CALL_INDICATOR_TEXTS = {
        "incoming video call", "incoming voice call",
        "apel video", "apel vocal", "apel audio",
        "whatsapp video", "whatsapp call",
        "video-anruf", "sprachanruf",
        "appel vidéo", "appel vocal"
    };

    private static final String[] NOT_A_CALL_TEXTS = {
        "no answer", "missed", "declined", "fara raspuns",
        "message", "mesaj", "record video note",
        "type a message", "online", "last seen", "typing"
    };

    private Handler handler;
    private boolean isProcessingCall = false;
    private long lastCallTime = 0;
    private long lastAnsweredTime = -99999;
    private static final long DEBOUNCE_MS = 4000;
    private static final long COOLDOWN_AFTER_ANSWER_MS = 12000;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WhatsAppAutoAnswer:WakeLock"
        );
        Log.i(TAG, "Serviciu pornit");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";

        boolean isWhatsApp = pkg.equals(WHATSAPP_PACKAGE) || pkg.equals(WHATSAPP_BUSINESS_PACKAGE);
        boolean isSystemUI = pkg.equals("com.android.systemui") || pkg.equals("com.huawei.systemui")
                          || pkg.equals("android") || pkg.equals("com.huawei.android.launcher");

        if (!isWhatsApp && !isSystemUI) return;

        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return;
        }

        long now = System.currentTimeMillis();
        if (isProcessingCall && now - lastCallTime < DEBOUNCE_MS) return;
        if (now - lastAnsweredTime < COOLDOWN_AFTER_ANSWER_MS) return;

        if (detectIncomingCall()) {
            Log.i(TAG, "Apel detectat! Trezesc ecranul si raspund...");
            lastCallTime = now;
            isProcessingCall = true;
            // Trezeste ecranul
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(30000);
            }
            handler.postDelayed(this::answerCall, 1500);
        }
    }

    private boolean detectIncomingCall() {
        List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                String screenText = extractText(root).toLowerCase();
                root.recycle();

                boolean isChatScreen = false;
                for (String notCall : NOT_A_CALL_TEXTS) {
                    if (screenText.contains(notCall)) { isChatScreen = true; break; }
                }
                if (isChatScreen) continue;

                if (screenText.contains("swipe up to accept") ||
                    screenText.contains("swipe to answer")) {
                    return true;
                }

                for (String indicator : CALL_INDICATOR_TEXTS) {
                    if (screenText.contains(indicator)) return true;
                }
            }
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            String text = extractText(root).toLowerCase();
            for (String notCall : NOT_A_CALL_TEXTS) {
                if (text.contains(notCall)) return false;
            }
            if (text.contains("swipe up to accept") || text.contains("swipe to answer")) return true;
            for (String id : ALL_ANSWER_IDS) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                if (nodes != null && !nodes.isEmpty()) return true;
            }
            for (String indicator : CALL_INDICATOR_TEXTS) {
                if (text.contains(indicator)) return true;
            }
        } finally {
            root.recycle();
        }
        return false;
    }

    private void answerCall() {
        boolean answered = false;

        // Incearca in toate ferestrele
        List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                try {
                    answered = clickAnswerButton(root);
                    if (answered) break;
                } finally {
                    root.recycle();
                }
            }
        }

        if (!answered) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                try {
                    answered = clickAnswerButton(root);
                } finally {
                    root.recycle();
                }
            }
        }

        if (answered) {
            Log.i(TAG, "Apel raspuns cu succes!");
            lastAnsweredTime = System.currentTimeMillis();
        } else {
            Log.w(TAG, "Nu am gasit butonul.");
        }

        handler.postDelayed(() -> {
            isProcessingCall = false;
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }, 5000);
    }

    private boolean clickAnswerButton(AccessibilityNodeInfo root) {
        for (String id : ALL_ANSWER_IDS) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (performClickOnNode(node)) return true;
                }
            }
        }
        return findAndClickByText(root);
    }

    private boolean findAndClickByText(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isClickable()) {
            String text = "";
            if (node.getText() != null) text = node.getText().toString().toLowerCase();
            if (node.getContentDescription() != null)
                text += " " + node.getContentDescription().toString().toLowerCase();
            for (String answerText : ANSWER_BUTTON_TEXTS) {
                if (text.contains(answerText) &&
                    !text.contains("decline") && !text.contains("refuz")) {
                    if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findAndClickByText(child)) { child.recycle(); return true; }
                child.recycle();
            }
        }
        return false;
    }

    private boolean performClickOnNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            boolean result = parent.isClickable() && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            parent.recycle();
            return result;
        }
        return false;
    }

    private String extractText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        if (node.getText() != null) sb.append(node.getText()).append(" ");
        if (node.getContentDescription() != null) sb.append(node.getContentDescription()).append(" ");
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { sb.append(extractText(child)); child.recycle(); }
        }
        return sb.toString();
    }

    @Override
    public void onInterrupt() { isProcessingCall = false; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
}
