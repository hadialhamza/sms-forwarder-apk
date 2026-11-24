package com.slothunter.sms;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Handles HTTP/HTTPS networking for the SMS gateway.
 * Supports configurable HTTP methods (PUT, POST) with JSON payloads.
 */
public class Request {

    private static final String TAG = "SlotHunter_Request";

    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_ERROR = "error";
    public static final String RESULT_RETRY = "error_retry";

    private final String payload;
    private final String method;
    private String error = null;
    private int responseCode = -1;
    private HttpURLConnection connection;

    public Request(String urlString, String payload) {
        this(urlString, payload, "PUT");
    }

    public Request(String urlString, String payload, String method) {
        this.payload = payload != null ? payload : "";
        this.method = (method != null && !method.trim().isEmpty()) ? method.trim().toUpperCase() : "PUT";

        URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed URL: " + urlString, e);
            this.error = RESULT_ERROR;
            return;
        }

        try {
            this.connection = (HttpURLConnection) url.openConnection();
            this.connection.setRequestMethod(this.method);
            this.connection.setConnectTimeout(15000);
            this.connection.setReadTimeout(15000);
            this.connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            this.connection.setRequestProperty("Accept", "application/json");
        } catch (IOException e) {
            Log.e(TAG, "Error opening connection: " + e.getMessage(), e);
            this.error = RESULT_ERROR;
        }
    }

    public int getResponseCode() {
        return this.responseCode;
    }

    public String execute() {
        if (this.error != null) {
            return this.error;
        }

        String result = RESULT_SUCCESS;

        try {
            this.connection.setDoOutput(true);

            byte[] payloadBytes = this.payload.getBytes(StandardCharsets.UTF_8);
            this.connection.setFixedLengthStreamingMode(payloadBytes.length);

            OutputStream out = new BufferedOutputStream(this.connection.getOutputStream());
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writer.write(this.payload);
            writer.flush();
            writer.close();
            out.close();

            this.responseCode = this.connection.getResponseCode();

            boolean isSuccess = this.responseCode >= 200 && this.responseCode < 300;
            InputStream responseStream = isSuccess ? this.connection.getInputStream() : this.connection.getErrorStream();
            if (responseStream != null) {
                new BufferedInputStream(responseStream).close();
            }

            if (!isSuccess) {
                Log.e(TAG, "HTTP " + this.method + " failed with code " + this.responseCode + " for " + this.connection.getURL());
                result = RESULT_RETRY;
            } else {
                Log.i(TAG, "HTTP " + this.method + " succeeded (code " + this.responseCode + ")");
            }
        } catch (IOException e) {
            Log.e(TAG, "Network IO error: " + e.getMessage(), e);
            result = RESULT_RETRY;
        } finally {
            if (this.connection != null) {
                this.connection.disconnect();
            }
        }

        return result;
    }
}
