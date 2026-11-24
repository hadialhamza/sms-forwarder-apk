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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * Main dashboard for Slot Hunter SMS Gateway.
 * Provides controls for master forwarding switch, dual SIM configuration,
 * SIM auto-detection, and status monitoring.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SlotHunter_MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 2001;

    private GatewaySettings settings;
    private SwitchCompat switchMaster;
    private TextView textStatus;
    private TextView textLogStatus;
    private TextInputEditText inputSim1;
    private TextInputEditText inputSim2;
    private MaterialButton btnAutoDetect;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = GatewaySettings.getInstance(this);

        initViews();
        loadSettings();
        checkAndRequestPermissions();

        if (settings.isForwardingEnabled()) {
            SmsReceiverService.start(this);
        }
    }

    private void initViews() {
        switchMaster = findViewById(R.id.switch_master);
        textStatus = findViewById(R.id.text_status);
        textLogStatus = findViewById(R.id.text_log_status);
        inputSim1 = findViewById(R.id.input_sim1);
        inputSim2 = findViewById(R.id.input_sim2);
        btnAutoDetect = findViewById(R.id.btn_auto_detect);
        btnSave = findViewById(R.id.btn_save);

        switchMaster.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.setForwardingEnabled(isChecked);
            updateStatusUi(isChecked);
            if (isChecked) {
                SmsReceiverService.start(MainActivity.this);
            }
        });

        btnSave.setOnClickListener(v -> saveSettings());
        btnAutoDetect.setOnClickListener(v -> detectSimNumbers());
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
            textStatus.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
            textLogStatus.setText(R.string.status_listening);
        } else {
            textStatus.setText(R.string.status_inactive);
            textStatus.setTextColor(ContextCompat.getColor(this, R.color.colorDanger));
            textLogStatus.setText(R.string.status_paused);
        }
    }

    private void saveSettings() {
        String sim1 = inputSim1.getText() != null ? inputSim1.getText().toString().trim() : "";
        String sim2 = inputSim2.getText() != null ? inputSim2.getText().toString().trim() : "";

        settings.setSim1Number(sim1);
        settings.setSim2Number(sim2);

        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Saved SIM 1: " + sim1 + ", SIM 2: " + sim2);
    }

    private void detectSimNumbers() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.READ_PHONE_STATE},
                    PERMISSION_REQUEST_CODE
            );
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
                            String number = info.getNumber();

                            if (number != null && !number.trim().isEmpty()) {
                                if (simSlotIndex == 0) {
                                    inputSim1.setText(number);
                                    detectedAny = true;
                                } else if (simSlotIndex == 1) {
                                    inputSim2.setText(number);
                                    detectedAny = true;
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
                        inputSim1.setText(line1);
                        detectedAny = true;
                    }
                }
            }

            if (detectedAny) {
                Toast.makeText(this, R.string.toast_sims_detected, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.toast_sims_not_detected, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error detecting SIMs: " + e.getMessage(), e);
            Toast.makeText(this, R.string.toast_sims_not_detected, Toast.LENGTH_SHORT).show();
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
            if (!allGranted) {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
            }
        }
    }
}
