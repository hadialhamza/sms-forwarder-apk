package com.slothunter.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

/**
 * Manifest-declared broadcast receiver that intercepts incoming SMS, filters for IVAC messages,
 * identifies the receiving physical SIM slot (SIM 1 vs SIM 2), and dispatches the payload to Firebase RTDB.
 */
public class SmsBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "SlotHunter_Receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

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

        // 3. Detect physical SIM slot (0 for SIM 1, 1 for SIM 2)
        int slotIndex = detectSimSlot(context, intent, bundle);
        int slotNumber = slotIndex + 1; // 1 or 2
        String slotLabel = "SIM " + slotNumber;

        // 4. Map SIM slot to configured phone number
        String phoneNumber = settings.getPhoneNumberForSlot(slotNumber);
        String cleanPhone = GatewaySettings.normalizePhoneNumber(phoneNumber);
        if (cleanPhone.isEmpty()) {
            cleanPhone = "SIM" + slotNumber;
        }

        // 5. Construct direct Firebase target URL (e.g. .../sms/01765060631.json)
        // No outer timestamp object wrapper
        String targetUrl = settings.buildTargetUrl(cleanPhone);

        // 6. Construct clean JSON payload
        JSONObject json = new JSONObject();
        try {
            json.put("sender", sender);
            json.put("text", smsBody);
            json.put("sim_slot", slotLabel);
            json.put("sim_number", cleanPhone);
            json.put("timestamp", timestamp);
        } catch (JSONException e) {
            Log.e(TAG, "JSON serialization error: " + e.getMessage(), e);
            return;
        }

        String payload = json.toString();
        Log.i(TAG, "Dispatching IVAC OTP for " + slotLabel + " (" + cleanPhone + ") to: " + targetUrl);

        // Record the event in history (stores full body and status)
        GatewaySettings.SmsEvent event = new GatewaySettings.SmsEvent(
                sender, smsBody, slotLabel, timestamp, GatewaySettings.STATUS_SENDING, targetUrl, payload
        );
        settings.addSmsEvent(event);

        // 7. Enqueue WorkManager request
        RequestWorker.enqueue(context, targetUrl, payload, "PUT", timestamp);
    }

    /**
     * Accurately detects the receiving physical SIM slot (0 for SIM 1, 1 for SIM 2).
     * Distinguishes physical slot indices (0, 1) from internal subscription IDs.
     */
    public int detectSimSlot(Context context, Intent intent, Bundle bundle) {
        // Priority 1: Check standard Android Telephony extra (android.telephony.extra.SLOT_INDEX)
        if (intent != null && intent.hasExtra("android.telephony.extra.SLOT_INDEX")) {
            int slot = intent.getIntExtra("android.telephony.extra.SLOT_INDEX", -1);
            if (slot == 0 || slot == 1) {
                return slot;
            }
        }

        if (bundle == null) {
            return 0; // Default to SIM 1
        }

        // Priority 2: Direct known physical slot keys
        String[] directSlotKeys = {"slot", "simSlot", "sim_slot", "slot_id", "simnum", "phone"};
        for (String key : directSlotKeys) {
            if (bundle.containsKey(key)) {
                Object val = bundle.get(key);
                int parsed = parseSlotValue(val);
                if (parsed == 0 || parsed == 1) {
                    return parsed;
                }
            }
        }

        // Priority 3: Subscription ID mapping via SubscriptionManager
        // Subscription IDs (e.g. 2, 3) must be mapped to physical slot (0 or 1)
        String[] subIdKeys = {
                "subscription", "subscription_id", "sub_id", "subId",
                "android.telephony.extra.SUBSCRIPTION_INDEX"
        };
        for (String subKey : subIdKeys) {
            if (bundle.containsKey(subKey)) {
                Object val = bundle.get(subKey);
                int subId = parseSlotValue(val);
                if (subId >= 0 && context != null) {
                    try {
                        SubscriptionManager sm = (SubscriptionManager)
                                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                        if (sm != null) {
                            SubscriptionInfo info = sm.getActiveSubscriptionInfo(subId);
                            if (info != null) {
                                int mappedSlot = info.getSimSlotIndex();
                                if (mappedSlot == 0 || mappedSlot == 1) {
                                    return mappedSlot;
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Unable to resolve subId " + subId + " to slot: " + e.getMessage());
                    }
                }
            }
        }

        // Priority 4: Search bundle for keys containing "slot" or "sim" (excluding "sub")
        Set<String> keys = bundle.keySet();
        for (String key : keys) {
            String lower = key.toLowerCase();
            if ((lower.contains("slot") || lower.contains("sim")) && !lower.contains("sub")) {
                Object val = bundle.get(key);
                int parsed = parseSlotValue(val);
                if (parsed == 0 || parsed == 1) {
                    return parsed;
                }
            }
        }

        // Default fallback to SIM 1 (0-based)
        return 0;
    }

    private int parseSlotValue(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }
}
