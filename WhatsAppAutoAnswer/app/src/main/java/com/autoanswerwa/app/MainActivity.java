package com.autoanswerwa.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * MainActivity - Interfata principala de configurare
 *
 * Permite utilizatorului sa:
 * 1. Activeze/dezactiveze serviciul de accesibilitate
 * 2. Aleaga raspuns VIDEO sau AUDIO
 * 3. Seteze delay-ul de raspuns (1-5 secunde)
 * 4. Vada statusul curent
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;

    private Switch switchEnabled;
    private RadioGroup radioGroupCallType;
    private RadioButton radioVideo, radioAudio;
    private SeekBar seekBarDelay;
    private TextView tvDelayValue, tvStatus, tvInstructions;
    private Button btnOpenAccessibility, btnSave;

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("wa_settings", MODE_PRIVATE);

        initViews();
        loadSettings();
        updateStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void initViews() {
        switchEnabled = findViewById(R.id.switch_enabled);
        radioGroupCallType = findViewById(R.id.radio_group_call_type);
        radioVideo = findViewById(R.id.radio_video);
        radioAudio = findViewById(R.id.radio_audio);
        seekBarDelay = findViewById(R.id.seekbar_delay);
        tvDelayValue = findViewById(R.id.tv_delay_value);
        tvStatus = findViewById(R.id.tv_status);
        tvInstructions = findViewById(R.id.tv_instructions);
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility);
        btnSave = findViewById(R.id.btn_save);

        // SeekBar 1-5 secunde
        seekBarDelay.setMax(4); // 0=1s, 1=2s, 2=3s, 3=4s, 4=5s
        seekBarDelay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int seconds = progress + 1;
                tvDelayValue.setText(seconds + " secunde");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnOpenAccessibility.setOnClickListener(v -> openAccessibilitySettings());
        btnSave.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        boolean preferVideo = prefs.getBoolean("prefer_video", true);
        int delayMs = prefs.getInt("answer_delay_ms", 1500);

        if (preferVideo) {
            radioVideo.setChecked(true);
        } else {
            radioAudio.setChecked(true);
        }

        int delaySeconds = (delayMs / 1000) - 1;
        if (delaySeconds < 0) delaySeconds = 0;
        if (delaySeconds > 4) delaySeconds = 4;
        seekBarDelay.setProgress(delaySeconds);
        tvDelayValue.setText((delaySeconds + 1) + " secunde");
    }

    private void saveSettings() {
        boolean preferVideo = radioVideo.isChecked();
        int delayMs = (seekBarDelay.getProgress() + 1) * 1000;

        prefs.edit()
            .putBoolean("prefer_video", preferVideo)
            .putInt("answer_delay_ms", delayMs)
            .apply();

        Toast.makeText(this, "✅ Setari salvate!", Toast.LENGTH_SHORT).show();
    }

    private void updateStatus() {
        boolean accessibilityEnabled = isAccessibilityServiceEnabled();

        if (accessibilityEnabled) {
            tvStatus.setText("✅ ACTIV - Serviciul ruleaza");
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark));
            tvInstructions.setVisibility(View.GONE);
            btnOpenAccessibility.setText("⚙️ Setari Accesibilitate");
        } else {
            tvStatus.setText("❌ INACTIV - Trebuie activat serviciul");
            tvStatus.setTextColor(getColor(android.R.color.holo_red_dark));
            tvInstructions.setVisibility(View.VISIBLE);
            btnOpenAccessibility.setText("👆 ACTIVEAZA ACUM");
        }
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this,
            "Cauta 'WhatsApp Auto Answer' si activeaza-l!",
            Toast.LENGTH_LONG).show();
    }

    /**
     * Verifica daca serviciul de accesibilitate este activ
     */
    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/" + WhatsAppCallService.class.getCanonicalName();
        try {
            int enabled = Settings.Secure.getInt(
                getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED
            );
            if (enabled != 1) return false;

            String settingValue = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );

            if (settingValue != null) {
                TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                splitter.setString(settingValue);
                while (splitter.hasNext()) {
                    if (splitter.next().equalsIgnoreCase(serviceName)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
