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
        prefs.edit().putString(KEY_SIM1_NUMBER, number != null ? number.trim() : "").apply();
    }

    public String getSim2Number() {
        return prefs.getString(KEY_SIM2_NUMBER, "");
    }

    public void setSim2Number(String number) {
        prefs.edit().putString(KEY_SIM2_NUMBER, number != null ? number.trim() : "").apply();
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
     */
    public String getPhoneNumberForSlot(int slotIndex) {
        if (slotIndex == 1) {
            return getSim1Number();
        } else if (slotIndex == 2) {
            return getSim2Number();
        }
        return "";
    }
}
