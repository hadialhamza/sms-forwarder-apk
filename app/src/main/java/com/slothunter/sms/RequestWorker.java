package com.slothunter.sms;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * WorkManager worker executing background HTTP requests with retry logic and backoff.
 */
public class RequestWorker extends Worker {

    private static final String TAG = "SlotHunter_Worker";

    public static final String DATA_URL = "url";
    public static final String DATA_PAYLOAD = "payload";
    public static final String DATA_METHOD = "method";
    public static final String DATA_MAX_RETRIES = "max_retries";

    private static final int DEFAULT_MAX_RETRIES = 10;

    public RequestWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data input = getInputData();
        String url = input.getString(DATA_URL);
        String payload = input.getString(DATA_PAYLOAD);
        String method = input.getString(DATA_METHOD);
        int maxRetries = input.getInt(DATA_MAX_RETRIES, DEFAULT_MAX_RETRIES);

        if (url == null || url.trim().isEmpty()) {
            Log.e(TAG, "Empty target URL, failing work.");
            return Result.failure();
        }

        int runAttempt = getRunAttemptCount();
        if (runAttempt > maxRetries) {
            Log.e(TAG, "Exceeded max retries (" + maxRetries + "). Failing task.");
            return Result.failure();
        }

        Log.i(TAG, "Dispatching HTTP " + method + " to " + url + " (Attempt: " + runAttempt + ")");
        Request request = new Request(url, payload, method);
        String result = request.execute();

        if (Request.RESULT_SUCCESS.equals(result)) {
            Log.i(TAG, "SMS successfully forwarded to endpoint.");
            return Result.success();
        }

        if (Request.RESULT_RETRY.equals(result)) {
            Log.w(TAG, "Temporary error during delivery, scheduling retry...");
            return Result.retry();
        }

        Log.e(TAG, "Permanent failure during dispatch.");
        return Result.failure();
    }

    /**
     * Enqueues a OneTimeWorkRequest to dispatch the payload to the given URL.
     */
    public static void enqueue(Context context, String url, String payload, String method) {
        Data data = new Data.Builder()
                .putString(DATA_URL, url)
                .putString(DATA_PAYLOAD, payload)
                .putString(DATA_METHOD, method)
                .putInt(DATA_MAX_RETRIES, DEFAULT_MAX_RETRIES)
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(RequestWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setInputData(data)
                .build();

        WorkManager.getInstance(context).enqueue(workRequest);
        Log.i(TAG, "Enqueued WorkRequest for URL: " + url);
    }
}
