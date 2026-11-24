package com.slothunter.sms;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages configuration, persistence, and dispatch event history for Slot Hunter SMS Gateway.
 */
public class GatewaySettings {

    private static final String PREF_NAME = "slot_hunter_gateway_prefs";

    public static final String KEY_ENABLED = "key_forwarding_enabled";
    public static final String KEY_SIM1_NUMBER = "key_sim1_number";
    public static final String KEY_SIM2_NUMBER = "key_sim2_number";
    public static final String KEY_FIREBASE_URL = "key_firebase_url";
    public static final String KEY_SMS_EVENTS = "key_sms_events_history";

    public static final String STATUS_SENDING = "SENDING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";

    public static final int MAX_EVENTS_HISTORY = 3;

    public static final String DEFAULT_FIREBASE_URL =
            "https://ivac-gateway-default-rtdb.asia-southeast1.firebasedatabase.app/sms/";

    private static volatile GatewaySettings instance;
    private static final Object EVENTS_LOCK = new Object();

    private final SharedPreferences prefs;

    public GatewaySettings(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static GatewaySettings getInstance(Context context) {
        if (instance == null) {
            synchronized (GatewaySettings.class) {
                if (instance == null) {
                    instance = new GatewaySettings(context);
                }
            }
        }
        return instance;
    }

    public SharedPreferences getPrefs() {
        return prefs;
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
     */
    public String getPhoneNumberForSlot(int slotIndex) {
        if (slotIndex == 2) {
            String sim2 = getSim2Number();
            if (!sim2.isEmpty()) {
                return sim2;
            }
        }
        String sim1 = getSim1Number();
        if (!sim1.isEmpty()) {
            return sim1;
        }
        return getSim2Number();
    }

    /**
     * Constructs the direct Firebase REST PUT URL for a target phone number and timestamp.
     */
    public String buildTargetUrl(String cleanPhoneNumber, long timestamp) {
        String base = getFirebaseUrl();
        String phone = (cleanPhoneNumber != null && !cleanPhoneNumber.trim().isEmpty())
                ? cleanPhoneNumber.trim()
                : "default";
        long ts = timestamp > 0 ? timestamp : System.currentTimeMillis();
        return base + phone + "/" + ts + ".json";
    }

    /**
     * Constructs the direct Firebase REST PUT URL using the current epoch timestamp.
     */
    public String buildTargetUrl(String cleanPhoneNumber) {
        return buildTargetUrl(cleanPhoneNumber, System.currentTimeMillis());
    }

    /**
     * Cleans and normalizes phone numbers by stripping country codes (+88, 88),
     * dashes, spaces, and formatting to standard 11 digits (e.g. 01XXXXXXXXX).
     */
    public static String normalizePhoneNumber(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("[\\s\\-\\(\\)]", "").trim();

        if (cleaned.startsWith("+880")) {
            cleaned = cleaned.substring(3);
        } else if (cleaned.startsWith("+88")) {
            cleaned = cleaned.substring(3);
        } else if (cleaned.startsWith("880")) {
            cleaned = cleaned.substring(2);
        }

        return cleaned;
    }

    // ==========================================
    // SMS EVENT HISTORY (MAX 3 ITEMS)
    // ==========================================

    public static class SmsEvent {
        public String sender;
        public String text;
        public String simSlot;
        public long timestamp;
        public String status;
        public String targetUrl;
        public String payload;

        public SmsEvent() {}

        public SmsEvent(String sender, String text, String simSlot, long timestamp, String status, String targetUrl, String payload) {
            this.sender = sender;
            this.text = text;
            this.simSlot = simSlot;
            this.timestamp = timestamp;
            this.status = status;
            this.targetUrl = targetUrl;
            this.payload = payload;
        }

        public JSONObject toJsonObject() {
            JSONObject json = new JSONObject();
            try {
                json.put("sender", sender);
                json.put("text", text);
                json.put("simSlot", simSlot);
                json.put("timestamp", timestamp);
                json.put("status", status);
                json.put("targetUrl", targetUrl);
                json.put("payload", payload);
            } catch (JSONException ignored) {}
            return json;
        }

        public static SmsEvent fromJsonObject(JSONObject json) {
            if (json == null) return null;
            SmsEvent event = new SmsEvent();
            event.sender = json.optString("sender", "UNKNOWN");
            event.text = json.optString("text", "");
            event.simSlot = json.optString("simSlot", "SIM 1");
            event.timestamp = json.optLong("timestamp", 0);
            event.status = json.optString("status", STATUS_SENDING);
            event.targetUrl = json.optString("targetUrl", "");
            event.payload = json.optString("payload", "");
            return event;
        }
    }

    public List<SmsEvent> getRecentEvents() {
        synchronized (EVENTS_LOCK) {
            List<SmsEvent> list = new ArrayList<>();
            String rawJson = prefs.getString(KEY_SMS_EVENTS, null);
            if (rawJson == null || rawJson.trim().isEmpty()) {
                return list;
            }
            try {
                JSONArray arr = new JSONArray(rawJson);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    SmsEvent event = SmsEvent.fromJsonObject(obj);
                    if (event != null) {
                        list.add(event);
                    }
                }
            } catch (JSONException ignored) {}
            return list;
        }
    }

    public void addSmsEvent(SmsEvent newEvent) {
        synchronized (EVENTS_LOCK) {
            List<SmsEvent> list = getRecentEvents();
            list.add(0, newEvent);
            while (list.size() > MAX_EVENTS_HISTORY) {
                list.remove(list.size() - 1);
            }
            saveEventsList(list);
        }
    }

    public void updateEventStatus(long timestamp, String newStatus) {
        synchronized (EVENTS_LOCK) {
            List<SmsEvent> list = getRecentEvents();
            boolean changed = false;
            for (SmsEvent event : list) {
                if (event.timestamp == timestamp) {
                    event.status = newStatus;
                    changed = true;
                    break;
                }
            }
            if (changed) {
                saveEventsList(list);
            }
        }
    }

    private void saveEventsList(List<SmsEvent> list) {
        JSONArray arr = new JSONArray();
        for (SmsEvent event : list) {
            arr.put(event.toJsonObject());
        }
        prefs.edit().putString(KEY_SMS_EVENTS, arr.toString()).apply();
    }
}
