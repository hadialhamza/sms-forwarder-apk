package com.slothunter.sms;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Main dashboard for Slot Hunter SMS Gateway.
 * Provides controls for master gateway switch, unified dual SIM routing,
 * automatic SIM detection on cold launch, and inline quick-save actions.
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
    }

    private void initViews() {
        switchMaster = findViewById(R.id.switch_master);
        textStatus = findViewById(R.id.text_status);
        inputSim1 = findViewById(R.id.input_sim1);
        inputSim2 = findViewById(R.id.input_sim2);
        btnSaveSim1 = findViewById(R.id.btn_save_sim1);
        btnSaveSim2 = findViewById(R.id.btn_save_sim2);

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

    private boolean hasPhonePermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Automatically resolves SIM slot numbers from hardware and saves them if found.
     */
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
