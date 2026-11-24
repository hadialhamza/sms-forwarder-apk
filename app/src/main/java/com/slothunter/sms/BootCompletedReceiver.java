package com.slothunter.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Automatically starts the SMS Gateway foreground service when the device boots up.
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        GatewaySettings settings = GatewaySettings.getInstance(context);
        if (settings.isForwardingEnabled()) {
            SmsReceiverService.start(context);
        }
    }
}
