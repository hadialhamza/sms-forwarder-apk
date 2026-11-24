package com.slothunter.sms;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit & Mock tests for BootCompletedReceiver.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class BootCompletedReceiverTest {

    @Mock
    private Context mockContext;

    @Mock
    private SharedPreferences mockPrefs;

    @Mock
    private Intent mockIntent;

    private BootCompletedReceiver receiver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getSharedPreferences(anyString(), eq(Context.MODE_PRIVATE)))
                .thenReturn(mockPrefs);

        receiver = new BootCompletedReceiver();
    }

    @Test
    public void testIgnoresNonBootAction() {
        when(mockIntent.getAction()).thenReturn(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        receiver.onReceive(mockContext, mockIntent);

        // SharedPreferences should never be accessed for unrelated broadcasts
        verify(mockPrefs, never()).getBoolean(eq(GatewaySettings.KEY_ENABLED), eq(true));
    }

    @Test
    public void testHandlesNullIntentGracefully() {
        receiver.onReceive(mockContext, null);
        verify(mockPrefs, never()).getBoolean(eq(GatewaySettings.KEY_ENABLED), eq(true));
    }
}
