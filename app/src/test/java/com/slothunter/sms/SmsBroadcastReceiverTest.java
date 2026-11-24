package com.slothunter.sms;

import android.os.Bundle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Unit & Mock tests for SmsBroadcastReceiver heuristics (e.g. SIM slot detection).
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class SmsBroadcastReceiverTest {

    @Mock
    private Bundle mockBundle;

    private SmsBroadcastReceiver receiver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        receiver = new SmsBroadcastReceiver();
    }

    @Test
    public void testDetectSim_SlotZeroAsSim1() {
        Set<String> keys = new HashSet<>();
        keys.add("simSlot");
        when(mockBundle.keySet()).thenReturn(keys);
        when(mockBundle.get("simSlot")).thenReturn(0);

        int slot = receiver.detectSim(mockBundle);
        assertEquals(0, slot);
    }

    @Test
    public void testDetectSim_SlotOneAsSim2() {
        Set<String> keys = new HashSet<>();
        keys.add("slot_id");
        when(mockBundle.keySet()).thenReturn(keys);
        when(mockBundle.get("slot_id")).thenReturn(1);

        int slot = receiver.detectSim(mockBundle);
        assertEquals(1, slot);
    }

    @Test
    public void testDetectSim_StringRepresentation() {
        Set<String> keys = new HashSet<>();
        keys.add("simId");
        when(mockBundle.keySet()).thenReturn(keys);
        when(mockBundle.get("simId")).thenReturn("1");

        int slot = receiver.detectSim(mockBundle);
        assertEquals(1, slot);
    }

    @Test
    public void testDetectSim_FallbackKeys() {
        Set<String> keys = new HashSet<>();
        keys.add("otherKey");
        when(mockBundle.keySet()).thenReturn(keys);
        when(mockBundle.get("otherKey")).thenReturn("ignored");

        when(mockBundle.containsKey("phone")).thenReturn(true);
        when(mockBundle.getInt("phone", -1)).thenReturn(0);

        int slot = receiver.detectSim(mockBundle);
        assertEquals(0, slot);
    }

    @Test
    public void testDetectSim_UndetectedReturnsNegative() {
        Set<String> keys = new HashSet<>();
        when(mockBundle.keySet()).thenReturn(keys);

        int slot = receiver.detectSim(mockBundle);
        assertEquals(-1, slot);
    }
}
