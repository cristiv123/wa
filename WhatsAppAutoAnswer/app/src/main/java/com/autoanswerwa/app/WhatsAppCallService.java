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

    // Texte specifice pentru butonul VIDEO (prioritate mare)
    private static final String[] VIDEO_BUTTON_TEXTS = {
        "answer video", "video answer", "video call",
        "raspunde video", "accepta video"
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

    private android.content.BroadcastReceiver cameraReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WhatsAppAutoAnswer:WakeLock"
        );

        // Receiver pentru semnalul de pornire camera
        cameraReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, android.content.Intent intent) {
                Log.i(TAG, "Primit semnal ENABLE_CAMERA");
                enableCamera();
            }
        };
        android.content.IntentFilter filter = new android.content.IntentFilter("com.autoanswerwa.ENABLE_CAMERA");
        registerReceiver(cameraReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

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
            // Incearca sa porneasca camera de 3 ori cu delay crescator
            handler.postDelayed(() -> enableCamera(), 2000);
            handler.postDelayed(() -> enableCamera(), 4000);
            handler.postDelayed(() -> enableCamera(), 6000);
        } else {
            Log.w(TAG, "Nu am gasit butonul.");
        }

        handler.postDelayed(() -> {
            isProcessingCall = false;
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }, 5000);
    }

    private boolean clickAnswerButton(AccessibilityNodeInfo root) {
        // Prioritate 1: cauta buton VIDEO dupa ID
        for (String id : ALL_ANSWER_IDS) {
            if (!id.contains("video")) continue;
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (performClickOnNode(node)) {
                        Log.i(TAG, "Click VIDEO button by ID: " + id);
                        return true;
                    }
                }
            }
        }

        // Prioritate 2: cauta buton VIDEO dupa text
        if (findAndClickByTextList(root, VIDEO_BUTTON_TEXTS)) return true;

        // Prioritate 3: cauta orice buton Answer dupa ID
        for (String id : ALL_ANSWER_IDS) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (performClickOnNode(node)) {
                        Log.i(TAG, "Click answer button by ID: " + id);
                        return true;
                    }
                }
            }
        }

        // Prioritate 4: cauta orice buton Answer dupa text
        return findAndClickByTextList(root, ANSWER_BUTTON_TEXTS);
    }

    private boolean findAndClickByTextList(AccessibilityNodeInfo node, String[] texts) {
        if (node == null) return false;
        if (node.isClickable()) {
            String text = "";
            if (node.getText() != null) text = node.getText().toString().toLowerCase();
            if (node.getContentDescription() != null)
                text += " " + node.getContentDescription().toString().toLowerCase();
            for (String t : texts) {
                if (text.contains(t) &&
                    !text.contains("decline") && !text.contains("refuz")) {
                    if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findAndClickByTextList(child, texts)) { child.recycle(); return true; }
                child.recycle();
            }
        }
        return false;
    }

    private void enableCamera() {
        Log.i(TAG, "Incerc sa pornesc camera...");
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            // Incearca in toate ferestrele
            List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                    root = window.getRoot();
                    if (root != null) break;
                }
            }
        }
        if (root == null) return;

        try {
            // ID-urile posibile ale butonului de camera in WhatsApp
            String[] cameraButtonIds = {
                "com.whatsapp:id/video_mute_btn",
                "com.whatsapp:id/camera_btn",
                "com.whatsapp:id/btn_camera",
                "com.whatsapp:id/video_btn",
                "com.whatsapp:id/mute_video_btn",
                "com.whatsapp:id/call_video_btn",
                "com.whatsapp:id/video_call_mute"
            };

            // Cauta dupa ID
            for (String id : cameraButtonIds) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                if (nodes != null && !nodes.isEmpty()) {
                    for (AccessibilityNodeInfo node : nodes) {
                        if (performClickOnNode(node)) {
                            Log.i(TAG, "Camera pornita via ID: " + id);
                            return;
                        }
                    }
                }
            }

            // Cauta dupa content description
            String[] cameraTexts = {
                "video paused", "start video", "camera",
                "turn on camera", "enable camera",
                "porneste camera", "video oprit"
            };
            if (findAndClickByTextList(root, cameraTexts)) {
                Log.i(TAG, "Camera pornita via text");
            }
        } finally {
            root.recycle();
        }
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
        if (cameraReceiver != null) {
            try { unregisterReceiver(cameraReceiver); } catch (Exception e) {}
        }
    }
}
