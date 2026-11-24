package com.slothunter.sms;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit and Mock tests for GatewaySettings logic using Mockito.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class GatewaySettingsTest {

    @Mock
    private Context mockContext;

    @Mock
    private SharedPreferences mockPrefs;

    @Mock
    private SharedPreferences.Editor mockEditor;

    private GatewaySettings settings;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getSharedPreferences(anyString(), eq(Context.MODE_PRIVATE)))
                .thenReturn(mockPrefs);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);

        settings = new GatewaySettings(mockContext);
    }

    @Test
    public void testIsForwardingEnabled_DefaultTrue() {
        when(mockPrefs.getBoolean(eq(GatewaySettings.KEY_ENABLED), eq(true))).thenReturn(true);
        assertTrue(settings.isForwardingEnabled());
    }

    @Test
    public void testSetForwardingEnabled() {
        settings.setForwardingEnabled(false);
        verify(mockEditor).putBoolean(GatewaySettings.KEY_ENABLED, false);
        verify(mockEditor).apply();
    }

    @Test
    public void testSimNumberRetrievalAndResolution() {
        when(mockPrefs.getString(eq(GatewaySettings.KEY_SIM1_NUMBER), anyString()))
                .thenReturn("01765060631");
        when(mockPrefs.getString(eq(GatewaySettings.KEY_SIM2_NUMBER), anyString()))
                .thenReturn("01719971636");

        assertEquals("01765060631", settings.getSim1Number());
        assertEquals("01719971636", settings.getSim2Number());

        // Slot 1 resolves to SIM 1
        assertEquals("01765060631", settings.getPhoneNumberForSlot(1));

        // Slot 2 resolves to SIM 2
        assertEquals("01719971636", settings.getPhoneNumberForSlot(2));

        // Fallback: slot > 2 should default to SIM 1 rather than producing slot3
        assertEquals("01765060631", settings.getPhoneNumberForSlot(3));
    }

    @Test
    public void testFirebaseUrlFormatting() {
        when(mockPrefs.getString(eq(GatewaySettings.KEY_FIREBASE_URL), anyString()))
                .thenReturn("https://my-firebase-app.firebasedatabase.app/sms");

        String formatted = settings.getFirebaseUrl();
        assertTrue(formatted.endsWith("/"));
        assertEquals("https://my-firebase-app.firebasedatabase.app/sms/", formatted);
    }

    @Test
    public void testNormalizePhoneNumber() {
        // Test standard 11-digit clean number
        assertEquals("01765060631", GatewaySettings.normalizePhoneNumber("01765060631"));

        // Test with +88 prefix
        assertEquals("01765060631", GatewaySettings.normalizePhoneNumber("+8801765060631"));

        // Test with 88 prefix without plus
        assertEquals("01765060631", GatewaySettings.normalizePhoneNumber("8801765060631"));

        // Test with dashes and spaces
        assertEquals("01765060631", GatewaySettings.normalizePhoneNumber("+88 017-65060631"));
        assertEquals("01719971636", GatewaySettings.normalizePhoneNumber("01719 971 636"));

        // Test null or empty
        assertEquals("", GatewaySettings.normalizePhoneNumber(null));
        assertEquals("", GatewaySettings.normalizePhoneNumber(""));
    }

    @Test
    public void testBuildTargetUrl_TimestampedHistoryEndpoint() {
        when(mockPrefs.getString(eq(GatewaySettings.KEY_FIREBASE_URL), anyString()))
                .thenReturn("https://ivac-gateway-default-rtdb.asia-southeast1.firebasedatabase.app/sms/");

        long timestamp = 1711234567890L;
        String targetUrl = settings.buildTargetUrl("01765060631", timestamp);
        assertEquals("https://ivac-gateway-default-rtdb.asia-southeast1.firebasedatabase.app/sms/01765060631/1711234567890.json", targetUrl);
    }
}
