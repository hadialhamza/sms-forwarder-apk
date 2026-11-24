package com.slothunter.sms;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Manages configuration and persistence for Slot Hunter SMS Gateway.
 */
public class GatewaySettings {

    private static final String PREF_NAME = "slot_hunter_gateway_prefs";

    public static final String KEY_ENABLED = "key_forwarding_enabled";
    public static final String KEY_SIM1_NUMBER = "key_sim1_number";
    public static final String KEY_SIM2_NUMBER = "key_sim2_number";
    public static final String KEY_FIREBASE_URL = "key_firebase_url";

    public static final String DEFAULT_FIREBASE_URL =
            "https://ivac-gateway-default-rtdb.asia-southeast1.firebasedatabase.app/sms/";

    private final SharedPreferences prefs;

    public GatewaySettings(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static GatewaySettings getInstance(Context context) {
        return new GatewaySettings(context);
    }

    public boolean isForwardingEnabled() {
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    public void setForwardingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public String getSim1Number() {
        return prefs.getString(KEY_SIM1_NUMBER, "");
    }

    public void setSim1Number(String number) {
        String normalized = normalizePhoneNumber(number);
        prefs.edit().putString(KEY_SIM1_NUMBER, normalized).apply();
    }

    public String getSim2Number() {
        return prefs.getString(KEY_SIM2_NUMBER, "");
    }

    public void setSim2Number(String number) {
        String normalized = normalizePhoneNumber(number);
        prefs.edit().putString(KEY_SIM2_NUMBER, normalized).apply();
    }

    public String getFirebaseUrl() {
        String url = prefs.getString(KEY_FIREBASE_URL, DEFAULT_FIREBASE_URL);
        if (url == null || url.trim().isEmpty()) {
            return DEFAULT_FIREBASE_URL;
        }
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }

    public void setFirebaseUrl(String url) {
        prefs.edit().putString(KEY_FIREBASE_URL, url != null ? url.trim() : DEFAULT_FIREBASE_URL).apply();
    }

    /**
     * Resolves the configured phone number for a given SIM slot index (1 or 2).
     * Strictly clamps to SIM 1 or SIM 2.
     */
    public String getPhoneNumberForSlot(int slotIndex) {
        if (slotIndex == 2) {
            String sim2 = getSim2Number();
            if (!sim2.isEmpty()) {
                return sim2;
            }
        }
        // Default to SIM 1 for slot 1 or fallback
        String sim1 = getSim1Number();
        if (!sim1.isEmpty()) {
            return sim1;
        }
        return getSim2Number();
    }

    /**
     * Constructs the direct Firebase REST PUT URL for a target phone number.
     * Example: https://.../sms/01765060631.json (NO nested timestamp object key).
     */
    public String buildTargetUrl(String cleanPhoneNumber) {
        String base = getFirebaseUrl();
        String phone = (cleanPhoneNumber != null && !cleanPhoneNumber.trim().isEmpty())
                ? cleanPhoneNumber.trim()
                : "default";
        return base + phone + ".json";
    }

    /**
     * Cleans and normalizes phone numbers by stripping country codes (+88, 88),
     * dashes, spaces, and formatting to standard 11 digits (e.g. 01XXXXXXXXX).
     */
    public static String normalizePhoneNumber(String raw) {
        if (raw == null) {
            return "";
        }
        // Remove all spaces, dashes, parentheses
        String cleaned = raw.replaceAll("[\\s\\-\\(\\)]", "").trim();

        // Strip leading +88 or 88
        if (cleaned.startsWith("+880")) {
            cleaned = cleaned.substring(3);
        } else if (cleaned.startsWith("+88")) {
            cleaned = cleaned.substring(3);
        } else if (cleaned.startsWith("880")) {
            cleaned = cleaned.substring(2);
        }

        return cleaned;
    }
}
