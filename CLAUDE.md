# Slot Hunter SMS

Dedicated Android SMS Gateway engineered for IVAC OTP forwarding to Google Firebase Realtime Database.

## Core Architecture

- **`SmsBroadcastReceiver`**: Manifest-declared broadcast receiver that intercepts incoming SMS, filters via `IvacFilter`, maps the receiving SIM slot to the saved phone number, and dispatches a WorkManager task.
- **`IvacFilter`**: Evaluates body text with dual conditions: keyword matching (`ivacbd`) and word-number density check.
- **`GatewaySettings`**: SharedPreferences-backed configuration for SIM 1, SIM 2, and the Master Gateway toggle.
- **`Request` & `RequestWorker`**: Dispatches dynamic HTTP `PUT` requests to Firebase Realtime Database with exponential backoff retries.
- **`SmsReceiverService`**: Foreground service keeping the process alive against aggressive OEM battery killers.
- **`MainActivity`**: Dedicated control panel to toggle gateway status, configure SIM numbers, and auto-detect SIM subscriptions.
