package com.autoanswerwa.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BootReceiver - Porneste aplicatia automat la restart tableta
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i("WA_AutoAnswer", "📱 Tableta repornita - serviciu activ");
            // Accessibility Service-ul se reactiveza automat de catre Android
            // Nu trebuie sa facem nimic explicit
        }
    }
}
