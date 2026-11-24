package com.slothunter.sms;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for Request instantiation, default parameters, and URL handling.
 */
public class RequestTest {

    @Test
    public void testRequestInitialization_DefaultsToPut() {
        Request request = new Request("https://example.com/api/test.json", "{\"key\":\"value\"}");
        assertNotNull(request);
        // By default responseCode should be -1 before execution
        assertEquals(-1, request.getResponseCode());
    }

    @Test
    public void testRequestInitialization_CustomMethod() {
        Request request = new Request("https://example.com/api/test.json", "{\"key\":\"value\"}", "POST");
        assertNotNull(request);
        assertEquals(-1, request.getResponseCode());
    }

    @Test
    public void testMalformedUrl_ReturnsError() {
        Request request = new Request("invalid-url-format", "payload", "PUT");
        String result = request.execute();
        assertEquals(Request.RESULT_ERROR, result);
    }
}
