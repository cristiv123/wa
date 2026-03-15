package com.autoanswerwa.app;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 80, 60, 40);

        TextView info = new TextView(this);
        info.setText("WhatsApp Auto Answer\n\nPentru a functiona si cu ecranul stins, activeaza AMBELE servicii:\n\n1. Accessibility Service\n2. Notification Access");
        info.setTextSize(15);
        info.setPadding(0, 0, 0, 30);
        layout.addView(info);

        Button btn1 = new Button(this);
        btn1.setText("1. Activeaza Accessibility Service");
        layout.addView(btn1);
        btn1.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "Cauta 'WhatsApp Auto Answer' si activeaza!", Toast.LENGTH_LONG).show();
        });

        TextView space = new TextView(this);
        space.setPadding(0, 20, 0, 0);
        layout.addView(space);

        Button btn2 = new Button(this);
        btn2.setText("2. Activeaza Notification Access");
        layout.addView(btn2);
        btn2.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            Toast.makeText(this, "Cauta 'WhatsApp Auto Answer' si activeaza!", Toast.LENGTH_LONG).show();
        });

        setContentView(layout);
    }
}
