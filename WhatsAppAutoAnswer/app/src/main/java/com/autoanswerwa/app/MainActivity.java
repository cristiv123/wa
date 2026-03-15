package com.autoanswerwa.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private RadioButton radioVideo, radioAudio;
    private SeekBar seekBarDelay;
    private TextView tvDelayValue, tvStatus;
    private Button btnOpenAccessibility, btnSave;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("wa_settings", MODE_PRIVATE);

        tvStatus = findViewById(R.id.tv_status);
        tvDelayValue = findViewById(R.id.tv_delay_value);
        radioVideo = findViewById(R.id.radio_video);
        radioAudio = findViewById(R.id.radio_audio);
        seekBarDelay = findViewById(R.id.seekbar_delay);
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility);
        btnSave = findViewById(R.id.btn_save);

        seekBarDelay.setMax(4);
        seekBarDelay.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvDelayValue.setText((progress + 1) + " secunde");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnOpenAccessibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                Toast.makeText(MainActivity.this,
                    "Cauta 'WhatsApp Auto Answer' si activeaza-l!",
                    Toast.LENGTH_LONG).show();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit()
                    .putBoolean("prefer_video", radioVideo.isChecked())
                    .putInt("answer_delay_ms", (seekBarDelay.getProgress() + 1) * 1000)
                    .apply();
                Toast.makeText(MainActivity.this, "Setari salvate!", Toast.LENGTH_SHORT).show();
            }
        });

        // Load settings
        boolean preferVideo = prefs.getBoolean("prefer_video", true);
        if (preferVideo) radioVideo.setChecked(true);
        else radioAudio.setChecked(true);

        int delayMs = prefs.getInt("answer_delay_ms", 1500);
        int progress = Math.min(Math.max((delayMs / 1000) - 1, 0), 4);
        seekBarDelay.setProgress(progress);
        tvDelayValue.setText((progress + 1) + " secunde");
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean active = isAccessibilityServiceEnabled();
        tvStatus.setText(active ? "ACTIV - Serviciul ruleaza" : "INACTIV - Trebuie activat serviciul");
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/" + WhatsAppCallService.class.getCanonicalName();
        try {
            int enabled = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED);
            if (enabled != 1) return false;
            String services = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (services != null) {
                TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                splitter.setString(services);
                while (splitter.hasNext()) {
                    if (splitter.next().equalsIgnoreCase(serviceName)) return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
