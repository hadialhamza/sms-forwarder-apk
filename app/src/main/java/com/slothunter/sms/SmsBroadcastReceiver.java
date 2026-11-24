package com.slothunter.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

/**
 * Manifest-declared broadcast receiver that intercepts incoming SMS, filters for IVAC messages,
 * identifies the receiving SIM slot, and dispatches the payload to Firebase RTDB.
 */
public class SmsBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "SlotHunter_Receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            return;
        }

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null || pdus.length == 0) {
            return;
        }

        // Reconstruct multi-part SMS content
        StringBuilder content = new StringBuilder();
        final SmsMessage[] messages = new SmsMessage[pdus.length];
        for (int i = 0; i < pdus.length; i++) {
            messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
            content.append(messages[i].getDisplayMessageBody());
        }

        String sender = messages[0].getOriginatingAddress();
        if (sender == null) {
            sender = "UNKNOWN";
        }

        String smsBody = content.toString();
        long timestamp = messages[0].getTimestampMillis();
        if (timestamp <= 0) {
            timestamp = System.currentTimeMillis();
        }

        GatewaySettings settings = GatewaySettings.getInstance(context);

        // 1. Check if forwarding is enabled
        if (!settings.isForwardingEnabled()) {
            Log.i(TAG, "SMS forwarding is currently DISABLED. Skipping message.");
            return;
        }

        // 2. Check IVAC filter conditions
        if (!IvacFilter.isIvacMessage(smsBody)) {
            Log.i(TAG, "Incoming SMS does not match IVAC criteria. Dropping.");
            return;
        }

        Log.i(TAG, "Verified IVAC SMS received from: " + sender);

        // 3. Detect SIM slot (1 or 2)
        int rawSlot = detectSim(bundle);
        int slotNumber = (rawSlot >= 0) ? rawSlot + 1 : 1;
        String slotLabel = "SIM " + slotNumber;

        // 4. Map SIM slot to configured phone number
        String phoneNumber = settings.getPhoneNumberForSlot(slotNumber);
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            phoneNumber = "slot" + slotNumber;
        }

        // Clean phone number for URL (remove non-alphanumeric if needed)
        String cleanPhone = phoneNumber.replaceAll("[^a-zA-Z0-9+_-]", "");
        if (cleanPhone.isEmpty()) {
            cleanPhone = "slot" + slotNumber;
        }

        // 5. Construct dynamic Firebase target URL
        // Target URL: Base URL + Phone Number + "/" + timestamp + ".json"
        String targetUrl = settings.getFirebaseUrl() + cleanPhone + "/" + timestamp + ".json";

        // 6. Construct JSON payload
        JSONObject json = new JSONObject();
        try {
            json.put("sender", sender);
            json.put("text", smsBody);
            json.put("sim_slot", slotLabel);
            json.put("sim_number", phoneNumber);
            json.put("timestamp", timestamp);
        } catch (JSONException e) {
            Log.e(TAG, "JSON serialization error: " + e.getMessage(), e);
            return;
        }

        String payload = json.toString();
        Log.i(TAG, "Dispatching IVAC OTP to: " + targetUrl);

        // 7. Enqueue WorkManager request
        RequestWorker.enqueue(context, targetUrl, payload, "PUT");
    }

    /**
     * Probes OEM-specific bundle extras to detect the receiving SIM slot index (0-based).
     */
    public int detectSim(Bundle bundle) {
        int slot = -1;

        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            String lower = key.toLowerCase();
            if (lower.contains("sim") || lower.contains("slot") || lower.contains("sub")) {
                Object value = bundle.get(key);
                if (value instanceof Integer) {
                    int intVal = (Integer) value;
                    if (intVal >= 0 && intVal <= 3) {
                        return intVal;
                    }
                } else if (value instanceof String) {
                    try {
                        int intVal = Integer.parseInt((String) value);
                        if (intVal >= 0 && intVal <= 3) {
                            return intVal;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        if (bundle.containsKey("phone")) {
            slot = bundle.getInt("phone", -1);
        } else if (bundle.containsKey("slot")) {
            slot = bundle.getInt("slot", -1);
        } else if (bundle.containsKey("simSlot")) {
            slot = bundle.getInt("simSlot", -1);
        } else if (bundle.containsKey("slot_id")) {
            slot = bundle.getInt("slot_id", -1);
        } else if (bundle.containsKey("simnum")) {
            slot = bundle.getInt("simnum", -1);
        }

        return slot;
    }
}
