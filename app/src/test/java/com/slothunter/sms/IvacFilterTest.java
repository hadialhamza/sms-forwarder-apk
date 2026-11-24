package com.slothunter.sms;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for IvacFilter logic.
 */
public class IvacFilterTest {

    @Test
    public void testCondition1_KeywordMatchesCaseInsensitive() {
        assertTrue(IvacFilter.isIvacMessage("(IVACBD) Your OTP code is 123456"));
        assertTrue(IvacFilter.isIvacMessage("Your code from ivacbd is 654321"));
        assertTrue(IvacFilter.isIvacMessage("IvacBd verification notification."));
    }

    @Test
    public void testCondition2_SpelledOutWordNumbers() {
        // Typical IVAC format with underscores or dashes
        assertTrue(IvacFilter.isIvacMessage("Type sequence Six_One_Four_One_Seven_Two to proceed."));
        assertTrue(IvacFilter.isIvacMessage("Security code: Zero-One-Zero-Zero-Eight-Two"));
        assertTrue(IvacFilter.isIvacMessage("code two five nine one"));
    }

    @Test
    public void testRejectsNormalMessages() {
        // Regular messages that do not contain 'ivacbd' and have < 4 number words
        assertFalse(IvacFilter.isIvacMessage("Hello, how are you? Meeting at 5pm."));
        assertFalse(IvacFilter.isIvacMessage("Bkash Cash In Tk 500.00 from 01700000000 successful."));
        assertFalse(IvacFilter.isIvacMessage("Please call me back at two pm."));
        assertFalse(IvacFilter.isIvacMessage("I will send you one or two files tomorrow."));
    }

    @Test
    public void testEdgeCases() {
        assertFalse(IvacFilter.isIvacMessage(null));
        assertFalse(IvacFilter.isIvacMessage(""));
        assertFalse(IvacFilter.isIvacMessage("   "));
    }

    @Test
    public void testCountWordNumbers() {
        assertEquals(0, IvacFilter.countWordNumbers("No number words here"));
        assertEquals(2, IvacFilter.countWordNumbers("one and two"));
        assertEquals(6, IvacFilter.countWordNumbers("Six_One_Four_One_Seven_Two"));
    }
}
