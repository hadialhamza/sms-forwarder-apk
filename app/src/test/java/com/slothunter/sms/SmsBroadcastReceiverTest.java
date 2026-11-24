package com.slothunter.sms;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit & Mock tests for SmsBroadcastReceiver heuristics and SIM slot detection.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class SmsBroadcastReceiverTest {

    @Mock
    private Context mockContext;

    @Mock
    private Intent mockIntent;

    @Mock
    private Bundle mockBundle;

    @Mock
    private SubscriptionManager mockSubManager;

    @Mock
    private SubscriptionInfo mockSubInfo;

    private SmsBroadcastReceiver receiver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        receiver = new SmsBroadcastReceiver();

        when(mockContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE))
                .thenReturn(mockSubManager);
    }

    @Test
    public void testDetectSim_OfficialExtraSlotIndexZero_ReturnsSim1() {
        // android.telephony.extra.SLOT_INDEX is the official Android API extra
        when(mockIntent.hasExtra("android.telephony.extra.SLOT_INDEX")).thenReturn(true);
        when(mockIntent.getIntExtra("android.telephony.extra.SLOT_INDEX", -1)).thenReturn(0);

        int slot = receiver.detectSimSlot(mockContext, mockIntent, mockBundle);
        assertEquals(0, slot); // 0-based for SIM 1
    }

    @Test
    public void testDetectSim_OfficialExtraSlotIndexOne_ReturnsSim2() {
        when(mockIntent.hasExtra("android.telephony.extra.SLOT_INDEX")).thenReturn(true);
        when(mockIntent.getIntExtra("android.telephony.extra.SLOT_INDEX", -1)).thenReturn(1);

        int slot = receiver.detectSimSlot(mockContext, mockIntent, mockBundle);
        assertEquals(1, slot); // 0-based for SIM 2
    }

    @Test
    public void testDetectSim_DirectSlotKeys_SlotZero() {
        Set<String> keys = new HashSet<>();
        keys.add("slot");
        when(mockBundle.keySet()).thenReturn(keys);
        when(mockBundle.get("slot")).thenReturn(0);

        int slot = receiver.detectSimSlot(mockContext, mockIntent, mockBundle);
        assertEquals(0, slot);
    }

    @Test
    public void testDetectSim_DirectSlotKeys_SlotOne() {
        Set<String> keys = new HashSet<>();
        keys.add("simSlot");
        when(mockBundle.keySet()).thenReturn(keys);
        when(mockBundle.get("simSlot")).thenReturn(1);

        int slot = receiver.detectSimSlot(mockContext, mockIntent, mockBundle);
        assertEquals(1, slot);
    }

    @Test
    public void testDetectSim_SubscriptionIdTwo_ResolvesViaSubManagerToSlotZero() {
        // Simulating user's real-device scenario:
        // SIM 1 has subscriptionId = 2. It must resolve to physical slot 0 (SIM 1), NOT slot 2!
        Set<String> keys = new HashSet<>();
        keys.add("subscription");
        when(mockBundle.keySet()).thenReturn(keys);
        when(mockBundle.get("subscription")).thenReturn(2);

        when(mockSubManager.getActiveSubscriptionInfo(eq(2))).thenReturn(mockSubInfo);
        when(mockSubInfo.getSimSlotIndex()).thenReturn(0); // Physical slot 0!

        int slot = receiver.detectSimSlot(mockContext, mockIntent, mockBundle);
        assertEquals(0, slot); // Must be 0 (SIM 1), NOT 2 or 3!
    }

    @Test
    public void testDetectSim_StrictClamping_NeverReturnsGreaterThanOne() {
        // If an anomalous OEM value 2 or 3 appears without mapping, clamp to 0 or 1
        Set<String> keys = new HashSet<>();
        keys.add("slot_id");
        when(mockBundle.keySet()).thenReturn(keys);
        when(mockBundle.get("slot_id")).thenReturn(3);

        int slot = receiver.detectSimSlot(mockContext, mockIntent, mockBundle);
        // Clamped: on a dual-SIM phone, slot can only be 0 or 1
        assertEquals(0, slot);
    }

    @Test
    public void testDetectSim_FallbackDefault() {
        Set<String> keys = new HashSet<>();
        when(mockBundle.keySet()).thenReturn(keys);

        int slot = receiver.detectSimSlot(mockContext, mockIntent, mockBundle);
        assertEquals(0, slot); // Default fallback is SIM 1 (index 0)
    }
}
