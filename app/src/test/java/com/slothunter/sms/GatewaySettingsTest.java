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
                .thenReturn("01711111111");
        when(mockPrefs.getString(eq(GatewaySettings.KEY_SIM2_NUMBER), anyString()))
                .thenReturn("01822222222");

        assertEquals("01711111111", settings.getSim1Number());
        assertEquals("01822222222", settings.getSim2Number());

        // Slot 1 should resolve to SIM 1
        assertEquals("01711111111", settings.getPhoneNumberForSlot(1));

        // Slot 2 should resolve to SIM 2
        assertEquals("01822222222", settings.getPhoneNumberForSlot(2));

        // Unknown slot should resolve to empty
        assertEquals("", settings.getPhoneNumberForSlot(3));
    }

    @Test
    public void testFirebaseUrlFormatting() {
        // Test ensuring trailing slash is always appended
        when(mockPrefs.getString(eq(GatewaySettings.KEY_FIREBASE_URL), anyString()))
                .thenReturn("https://my-firebase-app.firebasedatabase.app/sms");

        String formatted = settings.getFirebaseUrl();
        assertTrue(formatted.endsWith("/"));
        assertEquals("https://my-firebase-app.firebasedatabase.app/sms/", formatted);
    }
}
