package com.autoanswerwa.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class WhatsAppCallService extends AccessibilityService {

    private static final String TAG = "WA_AutoAnswer";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b";

    private PowerManager.WakeLock wakeLock;

    // Toate ID-urile posibile pentru butonul de raspuns video/audio
    private static final String[] ALL_ANSWER_IDS = {
        // Video answer buttons
        "com.whatsapp:id/answer_video_call_btn",
        "com.whatsapp:id/video_call_btn",
        "com.whatsapp:id/call_btn_answer_video",
        "com.whatsapp:id/answer_video",
        // Audio answer buttons  
        "com.whatsapp:id/answer_call_btn",
        "com.whatsapp:id/answer_voice_btn",
        "com.whatsapp:id/call_btn_answer_voice",
        "com.whatsapp:id/answer",
        // Generic answer button
        "com.whatsapp:id/btn_answer",
        "com.whatsapp:id/call_answer_btn",
        // WhatsApp Business
        "com.whatsapp.w4b:id/answer_video_call_btn",
        "com.whatsapp.w4b:id/answer_call_btn",
        "com.whatsapp.w4b:id/answer_video",
        "com.whatsapp.w4b:id/answer",
    };

    // Texte pe care le cauta pe butoane (pentru banner notifications)
    private static final String[] ANSWER_BUTTON_TEXTS = {
        "answer", "raspunde", "accepta", "accept",
        "video", "antworten", "répondre", "risposta"
    };

    // Texte care indica UN APEL ACTIV incoming
    private static final String[] CALL_INDICATOR_TEXTS = {
        "incoming video call", "incoming voice call",
        "apel video", "apel vocal", "apel audio",
        "whatsapp video", "whatsapp call",
        "video-anruf", "sprachanruf",
        "appel vidéo", "appel vocal"
    };

    // Texte care indica ca NU e un apel activ (istoric, chat, etc.)
    private static final String[] NOT_A_CALL_TEXTS = {
        "no answer", "missed", "declined", "fara raspuns",
        "nepreluат", "message", "mesaj", "record video note",
        "type a message", "online", "last seen", "typing"
    };

    private Handler handler;
    private boolean isProcessingCall = false;
    private long lastCallTime = 0;
    private long lastAnsweredTime = -99999;
    private static final long DEBOUNCE_MS = 4000;
    private static final long COOLDOWN_AFTER_ANSWER_MS = 12000; // 12 secunde pauza dupa raspuns

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        // Initializeaza WakeLock pentru a trezi ecranul la apel
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

        // Raspunde si la notificari sistem (banner-ul Huawei vine din system UI)
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
        // Nu face nimic 15 secunde dupa ce am raspuns (evita tap-uri gresite)
        if (now - lastAnsweredTime < COOLDOWN_AFTER_ANSWER_MS) return;

        if (detectIncomingCall()) {
            Log.i(TAG, "Apel detectat! Trezesc ecranul si raspund in 2 secunde...");
            lastCallTime = now;
            isProcessingCall = true;
            // Trezeste ecranul imediat
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(30000); // max 30 secunde
            }
            handler.postDelayed(this::answerCall, 2000);
        }
    }

    private boolean detectIncomingCall() {
        // Metoda 1: cauta in toate ferestrele active
        List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                String screenText = extractText(root).toLowerCase();
                root.recycle();

                // Daca e ecran de chat/istoric, ignora complet
                boolean isChatScreen = false;
                for (String notCall : NOT_A_CALL_TEXTS) {
                    if (screenText.contains(notCall)) {
                        isChatScreen = true;
                        break;
                    }
                }
                if (isChatScreen) continue;

                // Detecteaza si ecranul blocat cu "swipe up to accept"
                if (screenText.contains("swipe up to accept") ||
                    screenText.contains("swipe to answer") ||
                    screenText.contains("glisati in sus")) {
                    Log.d(TAG, "Detectat apel pe ecran blocat - swipe up necesar");
                    return true;
                }

                for (String indicator : CALL_INDICATOR_TEXTS) {
                    if (screenText.contains(indicator)) {
                        Log.d(TAG, "Gasit indicator apel: " + indicator);
                        return true;
                    }
                }
            }
        }

        // Metoda 2: cauta in fereastra activa
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            String text = extractText(root).toLowerCase();

            // Daca e ecran de chat, nu e apel activ
            for (String notCall : NOT_A_CALL_TEXTS) {
                if (text.contains(notCall)) return false;
            }

            // Ecran blocat cu swipe
            if (text.contains("swipe up to accept") || text.contains("swipe to answer")) {
                return true;
            }

            // Cauta butoane de raspuns
            for (String id : ALL_ANSWER_IDS) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                if (nodes != null && !nodes.isEmpty()) {
                    Log.d(TAG, "Gasit buton answer: " + id);
                    return true;
                }
            }
            // Cauta text indicator
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

        // Verifica daca e ecran blocat cu "swipe up to accept"
        if (isLockedScreenCall()) {
            Log.i(TAG, "Ecran blocat detectat - fac swipe up");
            doSwipeUp();
            lastAnsweredTime = System.currentTimeMillis();
            handler.postDelayed(() -> {
                isProcessingCall = false;
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            }, 5000);
            return;
        }

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

        // Incearca in fereastra activa
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
            Log.w(TAG, "Nu am gasit butonul de raspuns.");
        }

        handler.postDelayed(() -> {
            isProcessingCall = false;
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }, 5000);
    }

    private boolean isLockedScreenCall() {
        List<android.view.accessibility.AccessibilityWindowInfo> windows = getWindows();
        if (windows != null) {
            for (android.view.accessibility.AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root == null) continue;
                String text = extractText(root).toLowerCase();
                root.recycle();
                if (text.contains("swipe up to accept") || text.contains("swipe to answer")) {
                    return true;
                }
            }
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            try {
                String text = extractText(root).toLowerCase();
                if (text.contains("swipe up to accept") || text.contains("swipe to answer")) {
                    return true;
                }
            } finally {
                root.recycle();
            }
        }
        return false;
    }

    private void doSwipeUp() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        // Butonul verde "Swipe up to accept" e in centru-jos al ecranului
        // Glisam de la butonul verde in sus ~300px
        float startX = screenWidth * 0.50f;
        float startY = screenHeight * 0.82f;  // pozitia butonului verde
        float endY   = screenHeight * 0.50f;  // glisare in sus

        Log.d(TAG, "Swipe up: " + startX + " de la " + startY + " la " + endY);

        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(startX, endY);

        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 500))
            .build();
        dispatchGesture(gesture, null, null);
    }

    private boolean clickAnswerButton(AccessibilityNodeInfo root) {
        // 1. Cauta dupa ID
        for (String id : ALL_ANSWER_IDS) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null) {
                for (AccessibilityNodeInfo node : nodes) {
                    if (performClickOnNode(node)) return true;
                }
            }
        }

        // 2. Cauta dupa text pe butoane (pentru banner Huawei)
        return findAndClickByText(root);
    }

    private boolean findAndClickByText(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // Verifica daca acest nod e un buton cu text "ANSWER"
        if (node.isClickable()) {
            String text = "";
            if (node.getText() != null) text = node.getText().toString().toLowerCase();
            if (node.getContentDescription() != null)
                text += " " + node.getContentDescription().toString().toLowerCase();

            for (String answerText : ANSWER_BUTTON_TEXTS) {
                if (text.contains(answerText)) {
                    // Exclude butonul DECLINE
                    if (!text.contains("decline") && !text.contains("refuz") && !text.contains("respinge")) {
                        Log.d(TAG, "Gasit buton prin text: " + text);
                        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                    }
                }
            }
        }

        // Recursiv in copii
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (findAndClickByText(child)) {
                    child.recycle();
                    return true;
                }
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

    private void tryGestureTap() {
        // Tap pe pozitia butonului ANSWER din banner (dreapta sus pe ecran Huawei)
        // Banner-ul e ~200dp inaltime, butonul ANSWER e in dreapta
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        // Pozitia aproximativa a butonului ANSWER in banner-ul Huawei
        float tapX = screenWidth * 0.72f;  // "ANSWER" e spre dreapta
        float tapY = screenHeight * 0.22f; // Banner e sus

        Log.d(TAG, "Gesture tap la: " + tapX + ", " + tapY);
        Path path = new Path();
        path.moveTo(tapX, tapY);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 50))
            .build();
        dispatchGesture(gesture, null, null);
    }

    private String extractText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        if (node.getText() != null) sb.append(node.getText()).append(" ");
        if (node.getContentDescription() != null) sb.append(node.getContentDescription()).append(" ");
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                sb.append(extractText(child));
                child.recycle();
            }
        }
        return sb.toString();
    }

    @Override
    public void onInterrupt() {
        isProcessingCall = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }
}
