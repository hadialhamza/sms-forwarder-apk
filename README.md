# Slot Hunter SMS Gateway

Dedicated Android SMS Gateway engineered for IVAC (Indian Visa Application Center) OTP automation.

## Features

- **Automated IVAC Filtering:** Dual-condition text filter (keyword detection and spelled-out word-number density check) to capture OTP messages reliably across different telecom operator masks.
- **Dual SIM Support:** Automatically maps incoming SMS from SIM 1 or SIM 2 to configured target phone numbers.
- **Direct Firebase Integration:** Dispatches raw IVAC messages to Google Firebase Realtime Database via REST `PUT` requests under timestamp-keyed endpoints (`/sms/{target_phone_number}/{timestamp}.json`).
- **Resilient Background Execution:** Foreground listener service prevents process termination by aggressive OEM battery managers; WorkManager guarantees exponential backoff retries on network drops.
- **Clean Dashboard UI:** Simple control panel with master toggle, SIM configuration, auto-detection, and live activity logging.

## Tech Stack

- **Platform:** Android (Java, minSdk 14, targetSdk 33)
- **Background Tasks:** AndroidX WorkManager
- **Database Endpoint:** Firebase Realtime Database REST API

## Building the Project

Ensure you have JDK 17+ and Android SDK installed:

```bash
./gradlew assembleDebug      # Build debug APK
./gradlew testDebugUnitTest  # Run JVM unit tests
```
