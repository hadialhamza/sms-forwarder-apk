# Slot Hunter SMS Forwarder

A private, hardened, ultra-lightweight Android background daemon designed to intercept incoming IVAC OTP SMS, resolve active Dual-SIM hardware slots, and dispatch them directly to Google Firebase Realtime Database via REST `PUT`.

---

## Key Features

- **Resilient IVAC Filtering:** 2-condition body text filter requiring either the `ivacbd` keyword or a density of $\ge 4$ spelled-out digit names (`one`, `two`, etc.). Never misses an OTP even if sender headers change.
- **Dual-SIM Slot Auto-Detection:** 4-tier hardware heuristic to accurately detect whether an incoming message arrived on SIM 1 or SIM 2, mapping it to the configured phone number.
- **Zero-Failure Network Delivery:** WorkManager worker executing HTTP `PUT` with exponential backoff (up to 10 retries) to ensure delivery even through network drops.
- **Keep-Alive Daemon:** Foreground service (`SmsReceiverService`) displaying a persistent notification icon to survive aggressive OEM background memory killers (HiOS, MIUI, XOS, ColorOS).
- **Auto-Start on Boot:** Restores gateway listening automatically on device cold reboot (`BootCompletedReceiver`).
- **Ultra-Low Resource Footprint:** Pure Java 17 + Classic XML architecture consuming only ~15–20 MB RAM and an APK size of ~2 MB.

---

## Architecture Overview

```
Incoming SMS (Manifest Broadcast)
    │
    ▼
SmsBroadcastReceiver (Checks Master Toggle & IVAC Filter)
    │
    ▼
SIM Slot Detection (Heuristic Slot 0 vs Slot 1)
    │
    ▼
Lookup Configured Phone Number (from SharedPreferences)
    │
    ▼
WorkManager (RequestWorker HTTP PUT with Exponential Backoff)
    │
    ▼
Firebase Realtime Database REST API (/sms/{phone}/{timestamp}.json)
```

For the comprehensive technical specification, database schema, polling protocol, and setup guide, see [docs/architecture.md](file:///d:/Programming%20Data/My%20Projects/slot-hunter-apk/docs/architecture.md).

---

## Build & Installation

### Prerequisites
- JDK 17
- Android SDK (build-tools 33+)

### Build Commands

```bash
# Run JVM Unit Tests
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug

# Build Signed Release APK
./gradlew assembleRelease
```

The signed release APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`
