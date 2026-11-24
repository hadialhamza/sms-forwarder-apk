package com.slothunter.sms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Main dashboard for Slot Hunter SMS Gateway.
 * Provides controls for master gateway switch, unified dual SIM routing,
 * automatic SIM detection on launch, inline quick-save actions, and
 * a live dispatch monitor for the last 2-3 forwarded SMS events with manual resend.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SlotHunter_MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 2001;

    private GatewaySettings settings;
    private SwitchCompat switchMaster;
    private TextView textStatus;
    private EditText inputSim1;
    private EditText inputSim2;
    private ImageButton btnSaveSim1;
    private ImageButton btnSaveSim2;

    private TextView textEventsEmpty;
    private LinearLayout containerEvents;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault());
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = GatewaySettings.getInstance(this);

        initViews();
        loadSettings();
        checkAndRequestPermissions();

        // If permissions are already granted and no numbers configured, auto-detect on startup
        if (hasPhonePermissions()) {
            if (settings.getSim1Number().isEmpty() && settings.getSim2Number().isEmpty()) {
                autoDetectAndSaveSims(false);
            }
        }

        if (settings.isForwardingEnabled()) {
            SmsReceiverService.start(this);
        }

        renderEventsList();

        // Listen for live background updates to recent SMS events
        prefListener = (sharedPreferences, key) -> {
            if (GatewaySettings.KEY_SMS_EVENTS.equals(key)) {
                runOnUiThread(this::renderEventsList);
            }
        };
        settings.getPrefs().registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderEventsList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (prefListener != null) {
            settings.getPrefs().unregisterOnSharedPreferenceChangeListener(prefListener);
        }
    }

    private void initViews() {
        switchMaster = findViewById(R.id.switch_master);
        textStatus = findViewById(R.id.text_status);
        inputSim1 = findViewById(R.id.input_sim1);
        inputSim2 = findViewById(R.id.input_sim2);
        btnSaveSim1 = findViewById(R.id.btn_save_sim1);
        btnSaveSim2 = findViewById(R.id.btn_save_sim2);

        textEventsEmpty = findViewById(R.id.text_events_empty);
        containerEvents = findViewById(R.id.container_events);

        switchMaster.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setForwardingEnabled(isChecked);
            updateStatusUi(isChecked);
            if (isChecked) {
                SmsReceiverService.start(MainActivity.this);
            } else {
                SmsReceiverService.stop(MainActivity.this);
            }
        });

        btnSaveSim1.setOnClickListener(v -> saveSim1());
        btnSaveSim2.setOnClickListener(v -> saveSim2());
    }

    private void loadSettings() {
        boolean isEnabled = settings.isForwardingEnabled();
        switchMaster.setChecked(isEnabled);
        updateStatusUi(isEnabled);

        inputSim1.setText(settings.getSim1Number());
        inputSim2.setText(settings.getSim2Number());
    }

    private void updateStatusUi(boolean isEnabled) {
        if (isEnabled) {
            textStatus.setText(R.string.status_active);
            textStatus.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess));
        } else {
            textStatus.setText(R.string.status_inactive);
            textStatus.setTextColor(ContextCompat.getColor(this, R.color.colorDanger));
        }
    }

    private void saveSim1() {
        String raw = inputSim1.getText() != null ? inputSim1.getText().toString() : "";
        String clean = GatewaySettings.normalizePhoneNumber(raw);
        inputSim1.setText(clean);
        settings.setSim1Number(clean);
        Toast.makeText(this, R.string.toast_sim1_saved, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Saved SIM 1: " + clean);
    }

    private void saveSim2() {
        String raw = inputSim2.getText() != null ? inputSim2.getText().toString() : "";
        String clean = GatewaySettings.normalizePhoneNumber(raw);
        inputSim2.setText(clean);
        settings.setSim2Number(clean);
        Toast.makeText(this, R.string.toast_sim2_saved, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Saved SIM 2: " + clean);
    }

    // ==========================================
    // RECENT DISPATCH EVENTS MONITOR
    // ==========================================

    private void renderEventsList() {
        List<GatewaySettings.SmsEvent> events = settings.getRecentEvents();
        if (events.isEmpty()) {
            textEventsEmpty.setVisibility(View.VISIBLE);
            containerEvents.setVisibility(View.GONE);
            containerEvents.removeAllViews();
            return;
        }

        textEventsEmpty.setVisibility(View.GONE);
        containerEvents.setVisibility(View.VISIBLE);
        containerEvents.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < events.size(); i++) {
            GatewaySettings.SmsEvent event = events.get(i);
            View itemView = inflater.inflate(R.layout.item_sms_event, containerEvents, false);

            TextView textSim = itemView.findViewById(R.id.text_event_sim);
            TextView textSender = itemView.findViewById(R.id.text_event_sender);
            TextView textTime = itemView.findViewById(R.id.text_event_time);
            TextView textEventStatus = itemView.findViewById(R.id.text_event_status);
            TextView textBody = itemView.findViewById(R.id.text_event_body);
            MaterialButton btnResend = itemView.findViewById(R.id.btn_event_resend);
            View divider = itemView.findViewById(R.id.divider_event);

            // SIM Slot badge styling
            if ("SIM 2".equalsIgnoreCase(event.simSlot)) {
                textSim.setText(R.string.label_sim2);
                textSim.setBackgroundResource(R.drawable.bg_badge_sim2);
                textSim.setTextColor(ContextCompat.getColor(this, R.color.colorSim2Text));
            } else {
                textSim.setText(R.string.label_sim1);
                textSim.setBackgroundResource(R.drawable.bg_badge_sim1);
                textSim.setTextColor(ContextCompat.getColor(this, R.color.colorSim1Text));
            }

            // Sender & Formatted Timestamp
            textSender.setText(event.sender != null ? event.sender : "SMS");
            if (event.timestamp > 0) {
                textTime.setText(timeFormat.format(new Date(event.timestamp)));
            } else {
                textTime.setText("");
            }

            // Status indicator pill & actions
            if (GatewaySettings.STATUS_DELIVERED.equalsIgnoreCase(event.status)) {
                textEventStatus.setText("● DELIVERED");
                textEventStatus.setTextColor(ContextCompat.getColor(this, R.color.colorSuccess));
                btnResend.setVisibility(View.GONE);
            } else if (GatewaySettings.STATUS_FAILED.equalsIgnoreCase(event.status)) {
                textEventStatus.setText("● FAILED");
                textEventStatus.setTextColor(ContextCompat.getColor(this, R.color.colorDanger));
                btnResend.setVisibility(View.VISIBLE);
                btnResend.setOnClickListener(v -> resendEvent(event));
            } else {
                textEventStatus.setText("● SENDING…");
                textEventStatus.setTextColor(ContextCompat.getColor(this, R.color.colorWarning));
                btnResend.setVisibility(View.GONE);
            }

            // Full SMS message text
            textBody.setText(event.text != null ? event.text : "");

            // Hide divider on the last item
            if (i == events.size() - 1) {
                divider.setVisibility(View.GONE);
            } else {
                divider.setVisibility(View.VISIBLE);
            }

            containerEvents.addView(itemView);
        }
    }

    private void resendEvent(GatewaySettings.SmsEvent event) {
        if (event == null || event.targetUrl == null || event.payload == null) {
            return;
        }

        // Set status to sending and re-enqueue WorkManager
        settings.updateEventStatus(event.timestamp, GatewaySettings.STATUS_SENDING);
        RequestWorker.enqueue(this, event.targetUrl, event.payload, "PUT", event.timestamp);

        Toast.makeText(this, "Resending SMS…", Toast.LENGTH_SHORT).show();
        renderEventsList();
    }

    // ==========================================
    // AUTO DETECTION & PERMISSIONS
    // ==========================================

    private boolean hasPhonePermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED;
    }

    private void autoDetectAndSaveSims(boolean showToast) {
        if (!hasPhonePermissions()) {
            return;
        }

        try {
            boolean detectedAny = false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                SubscriptionManager subManager =
                        (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (subManager != null) {
                    @SuppressLint("MissingPermission")
                    List<SubscriptionInfo> subs = subManager.getActiveSubscriptionInfoList();
                    if (subs != null && !subs.isEmpty()) {
                        for (SubscriptionInfo info : subs) {
                            int simSlotIndex = info.getSimSlotIndex();
                            String number = "";
                            if (Build.VERSION.SDK_INT >= 33) {
                                try {
                                    number = subManager.getPhoneNumber(info.getSubscriptionId());
                                } catch (Exception ignored) {}
                            }
                            if (number == null || number.trim().isEmpty()) {
                                number = info.getNumber();
                            }

                            if (number != null && !number.trim().isEmpty()) {
                                String clean = GatewaySettings.normalizePhoneNumber(number);
                                if (!clean.isEmpty()) {
                                    if (simSlotIndex == 0) {
                                        inputSim1.setText(clean);
                                        settings.setSim1Number(clean);
                                        detectedAny = true;
                                    } else if (simSlotIndex == 1) {
                                        inputSim2.setText(clean);
                                        settings.setSim2Number(clean);
                                        detectedAny = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!detectedAny) {
                TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    @SuppressLint("MissingPermission")
                    String line1 = tm.getLine1Number();
                    if (line1 != null && !line1.trim().isEmpty()) {
                        String clean = GatewaySettings.normalizePhoneNumber(line1);
                        if (!clean.isEmpty()) {
                            inputSim1.setText(clean);
                            settings.setSim1Number(clean);
                            detectedAny = true;
                        }
                    }
                }
            }

            if (detectedAny && showToast) {
                Toast.makeText(this, R.string.toast_sims_auto_detected, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in autoDetectAndSaveSims: " + e.getMessage(), e);
        }
    }

    private void checkAndRequestPermissions() {
        List<String> requiredPermissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.RECEIVE_SMS);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.READ_PHONE_STATE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS)
                != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.READ_PHONE_NUMBERS);
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!requiredPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    requiredPermissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                if (settings.getSim1Number().isEmpty() && settings.getSim2Number().isEmpty()) {
                    autoDetectAndSaveSims(true);
                }
            } else {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
            }
        }
    }
}
