package com.autoanswerwa.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * WhatsAppCallService
 *
 * Detecteaza apelurile WhatsApp (audio si video) si raspunde automat.
 * Functioneaza prin Accessibility Service - simuleaza apasarea butonului de raspuns.
 *
 * Compatibil cu:
 * - WhatsApp normal (com.whatsapp)
 * - WhatsApp Business (com.whatsapp.w4b)
 * - Huawei EMUI / HarmonyOS
 * - Android 8+
 */
public class WhatsAppCallService extends AccessibilityService {

    private static final String TAG = "WA_AutoAnswer";

    // Package-urile WhatsApp monitorizate
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b";

    // ID-urile butoanelor din interfata WhatsApp (pot varia dupa versiune)
    // VIDEO call answer buttons
    private static final String[] VIDEO_BUTTON_IDS = {
        "com.whatsapp:id/answer_video_call_btn",
        "com.whatsapp:id/video_call_btn",
        "com.whatsapp:id/call_btn_answer_video",
        "com.whatsapp.w4b:id/answer_video_call_btn",
        "com.whatsapp.w4b:id/video_call_btn"
    };

    // AUDIO call answer buttons
    private static final String[] AUDIO_BUTTON_IDS = {
        "com.whatsapp:id/answer_call_btn",
        "com.whatsapp:id/answer_voice_btn",
        "com.whatsapp:id/call_btn_answer_voice",
        "com.whatsapp.w4b:id/answer_call_btn",
        "com.whatsapp.w4b:id/answer_voice_btn"
    };

    // Text-uri care apar pe ecranul de apel incoming
    private static final String[] INCOMING_CALL_TEXTS = {
        "incoming video call", "apel video", "video-anruf",
        "incoming voice call", "apel vocal", "sprachanruf",
        "whatsapp video", "whatsapp call"
    };

    private Handler handler;
    private boolean isProcessingCall = false;
    private long lastCallTime = 0;
    private static final long DEBOUNCE_MS = 3000; // 3 secunde intre apeluri

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        Log.i(TAG, "✅ WhatsApp Auto Answer Service pornit!");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        String packageName = "";
        if (event.getPackageName() != null) {
            packageName = event.getPackageName().toString();
        }

        // Filtram doar WhatsApp
        if (!packageName.equals(WHATSAPP_PACKAGE) &&
            !packageName.equals(WHATSAPP_BUSINESS_PACKAGE)) {
            return;
        }

        int eventType = event.getEventType();

        // Verificam la schimbari de fereastra si continut
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {

            // Debounce - nu procesam la interval prea scurt
            long now = System.currentTimeMillis();
            if (now - lastCallTime < DEBOUNCE_MS && isProcessingCall) {
                return;
            }

            // Verificam daca este un apel incoming
            if (isIncomingCall(event)) {
                Log.i(TAG, "📞 Apel detectat! Pornesc raspunsul automat...");
                lastCallTime = now;
                isProcessingCall = true;

                // Delay de 1.5 secunde inainte de a raspunde
                // (interfata WhatsApp are nevoie de timp sa se incarce)
                int delayMs = getAnswerDelay();
                handler.postDelayed(() -> {
                    answerCall();
                }, delayMs);
            }
        }
    }

    /**
     * Verifica daca evenimentul este un apel WhatsApp incoming
     */
    private boolean isIncomingCall(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return false;

        try {
            // Metoda 1: Cautam butoanele de raspuns in interfata
            for (String buttonId : VIDEO_BUTTON_IDS) {
                List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(buttonId);
                if (nodes != null && !nodes.isEmpty()) {
                    Log.d(TAG, "🎥 Gasit buton VIDEO: " + buttonId);
                    return true;
                }
            }

            for (String buttonId : AUDIO_BUTTON_IDS) {
                List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(buttonId);
                if (nodes != null && !nodes.isEmpty()) {
                    Log.d(TAG, "🔊 Gasit buton AUDIO: " + buttonId);
                    return true;
                }
            }

            // Metoda 2: Cautam text specific apelului incoming
            String screenText = getScreenText(rootNode).toLowerCase();
            for (String callText : INCOMING_CALL_TEXTS) {
                if (screenText.contains(callText)) {
                    Log.d(TAG, "📝 Gasit text apel: " + callText);
                    return true;
                }
            }

        } finally {
            rootNode.recycle();
        }

        return false;
    }

    /**
     * Raspunde la apel (video sau audio, configurabil din Settings)
     */
    private void answerCall() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.w(TAG, "⚠️ Root node null, incerc din nou...");
            isProcessingCall = false;
            return;
        }

        try {
            boolean answered = false;
            boolean preferVideo = getPreferVideo();

            if (preferVideo) {
                // Incearca intai VIDEO
                answered = clickButton(rootNode, VIDEO_BUTTON_IDS);
                if (!answered) {
                    // Fallback la AUDIO daca nu gaseste video
                    answered = clickButton(rootNode, AUDIO_BUTTON_IDS);
                }
            } else {
                // Incearca intai AUDIO
                answered = clickButton(rootNode, AUDIO_BUTTON_IDS);
                if (!answered) {
                    answered = clickButton(rootNode, VIDEO_BUTTON_IDS);
                }
            }

            if (answered) {
                Log.i(TAG, "✅ Apel raspuns cu succes!");
            } else {
                Log.w(TAG, "⚠️ Nu am gasit butonul! Incerc gesture tap...");
                // Ultima solutie: tap pe pozitia aproximativa a butonului de raspuns
                tryGestureTap();
            }

        } finally {
            rootNode.recycle();
            // Reset dupa 5 secunde
            handler.postDelayed(() -> isProcessingCall = false, 5000);
        }
    }

    /**
     * Cauta si apasa un buton dupa ID
     */
    private boolean clickButton(AccessibilityNodeInfo rootNode, String[] buttonIds) {
        for (String buttonId : buttonIds) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(buttonId);
            if (nodes != null && !nodes.isEmpty()) {
                AccessibilityNodeInfo button = nodes.get(0);
                if (button.isClickable()) {
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.i(TAG, "✅ Click pe buton: " + buttonId);
                    return true;
                } else {
                    // Incearca parent-ul
                    AccessibilityNodeInfo parent = button.getParent();
                    if (parent != null && parent.isClickable()) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        Log.i(TAG, "✅ Click pe parent: " + buttonId);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Fallback: tap gestural pe zona butonului de raspuns
     * Pe ecranele de apel WhatsApp, butonul de raspuns e de obicei in dreapta jos
     */
    private void tryGestureTap() {
        // Obtine dimensiunile ecranului
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        Rect bounds = new Rect();
        rootNode.getBoundsInScreen(bounds);
        rootNode.recycle();

        int screenWidth = bounds.right;
        int screenHeight = bounds.bottom;

        // Pozitia aproximativa a butonului "Raspunde video" (dreapta jos pe ecran)
        // Acestea sunt coordonate procentuale adaptabile la orice rezolutie
        float tapX = screenWidth * 0.75f;
        float tapY = screenHeight * 0.82f;

        Log.d(TAG, "🖱️ Gesture tap la: " + tapX + ", " + tapY);

        Path tapPath = new Path();
        tapPath.moveTo(tapX, tapY);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(tapPath, 0, 100));
        dispatchGesture(gestureBuilder.build(), null, null);
    }

    /**
     * Extrage tot textul vizibil pe ecran
     */
    private String getScreenText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder sb = new StringBuilder();
        CharSequence text = node.getText();
        if (text != null) sb.append(text).append(" ");
        CharSequence desc = node.getContentDescription();
        if (desc != null) sb.append(desc).append(" ");
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                sb.append(getScreenText(child));
                child.recycle();
            }
        }
        return sb.toString();
    }

    private boolean getPreferVideo() {
        SharedPreferences prefs = getSharedPreferences("wa_settings", MODE_PRIVATE);
        return prefs.getBoolean("prefer_video", true); // default: VIDEO
    }

    private int getAnswerDelay() {
        SharedPreferences prefs = getSharedPreferences("wa_settings", MODE_PRIVATE);
        return prefs.getInt("answer_delay_ms", 1500); // default: 1.5 secunde
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "⚠️ Serviciu intrerupt");
        isProcessingCall = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) handler.removeCallbacksAndMessages(null);
        Log.i(TAG, "🛑 Serviciu oprit");
    }
}
