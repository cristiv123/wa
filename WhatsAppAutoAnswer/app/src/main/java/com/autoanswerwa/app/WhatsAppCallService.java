package com.autoanswerwa.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class WhatsAppCallService extends AccessibilityService {

    private static final String TAG = "WA_AutoAnswer";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b";

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

    // Texte care indica un apel incoming
    private static final String[] CALL_INDICATOR_TEXTS = {
        "incoming video call", "incoming voice call",
        "apel video", "apel vocal", "apel audio",
        "video call", "voice call",
        "whatsapp video", "whatsapp call",
        "video-anruf", "sprachanruf",
        "appel vidéo", "appel vocal"
    };

    private Handler handler;
    private boolean isProcessingCall = false;
    private long lastCallTime = 0;
    private static final long DEBOUNCE_MS = 4000;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
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

        if (detectIncomingCall()) {
            Log.i(TAG, "Apel detectat! Raspund in 2 secunde...");
            lastCallTime = now;
            isProcessingCall = true;
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
            // Cauta butoane de raspuns
            for (String id : ALL_ANSWER_IDS) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
                if (nodes != null && !nodes.isEmpty()) {
                    Log.d(TAG, "Gasit buton answer: " + id);
                    return true;
                }
            }
            // Cauta text indicator
            String text = extractText(root).toLowerCase();
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
        } else {
            Log.w(TAG, "Nu am gasit buton, incerc tap gestural...");
            tryGestureTap();
        }

        handler.postDelayed(() -> isProcessingCall = false, 5000);
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

        // Al doilea tap - pozitia butonului video in ecranul complet de apel
        handler.postDelayed(() -> {
            float tapX2 = screenWidth * 0.75f;
            float tapY2 = screenHeight * 0.82f;
            Path path2 = new Path();
            path2.moveTo(tapX2, tapY2);
            GestureDescription gesture2 = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path2, 0, 50))
                .build();
            dispatchGesture(gesture2, null, null);
        }, 500);
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
